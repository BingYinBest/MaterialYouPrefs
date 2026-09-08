# 数据层设计

## 1. Room Schema

```kotlin
@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey val name: String,        // e.g. "add_hashtree_footer"
    val tab: String,                     // home / feature / settings
    val groupTitle: String,              // e.g. "密钥管理"
    val title: String,                   // 用户可见标题
    val subtitle: String,                // 一句话摘要
    val description: String?,            // 从 --help 抓的详细描述
    val iconName: String,                // Material Icon 名（反射解析）
    val sortOrder: Int,
    val priority: String,                // P0 / P1
    val paramsJson: String?,             // 参数签名（JSON 数组）
    val fetchedAt: Long?,                // 从 --help 动态拉取的时间戳
    val isBuiltin: Boolean,              // 是否来自种子数据
)

@Dao
interface CommandDao {
    @Query("SELECT * FROM commands WHERE tab = :tab ORDER BY sortOrder")
    suspend fun byTab(tab: String): List<CommandEntity>

    @Query("SELECT * FROM commands WHERE name = :name")
    suspend fun byName(name: String): CommandEntity?

    @Upsert suspend fun upsert(cmd: CommandEntity)
}

@Entity(tableName = "execution_history")
data class ExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val command: String,
    val argsJson: String,
    val exitCode: Int,
    val stdout: String?,
    val stderr: String?,
    val outputFilesJson: String?,
    val createdAt: Long,
)
```

## 2. Repository 接口

```kotlin
class CommandRepository(
    private val dao: CommandDao,
    private val avbRunner: AvbToolRunner,
) {
    suspend fun getCommandsByTab(tab: String): List<CommandEntity> {
        val db = dao.byTab(tab)
        if (db.isEmpty()) {
            seedFromAssetsAndHelp()
        }
        return dao.byTab(tab)
    }

    suspend fun getCommand(name: String): CommandEntity {
        val existing = dao.byName(name)
        if (existing != null && existing.fetchedAt != null &&
            System.currentTimeMillis() - existing.fetchedAt < 24 * 3600 * 1000L) {
            return existing
        }
        // 动态拉参数签名
        val help = avbRunner.fetchHelp(name)
        val updated = existing?.copy(
            paramsJson = help.paramsJson,
            description = help.description,
            fetchedAt = System.currentTimeMillis(),
        ) ?: parseFromHelp(name, help)
        dao.upsert(updated)
        return updated
    }

    private suspend fun seedFromAssetsAndHelp() {
        // 读 assets/commands_seed.json，逐条 upsert；再触发 help 拉取
    }
}

class AvbToolRunner(private val py: Python) {
    suspend fun run(cmd: String, args: List<String>): AvbResult
    suspend fun fetchHelp(cmd: String): HelpResult
}
```

## 3. 动态拉参数的机制（B 方案核心）

`avbtool --help <cmd>` 输出格式（已验证）：

```
usage: avbtool add_hashtree_footer [-h] --image IMAGE --partition_name NAME ...

Add a hashtree footer to the given file.
```

解析规则：
1. `--help` 抓 usage 行 → 抽出所有 `--xxx` / `-x` 参数
2. 抓 description 段（第一行非空文本）
3. 抓可选参数说明（argparse 会格式化出 `[options]`）
4. 组装成 `paramsJson`：
   ```json
   [
     {"name":"--image","required":true,"type":"file"},
     {"name":"--partition_name","required":true,"type":"string"},
     {"name":"--algorithm","required":false,"type":"choice","choices":["sha256","sha512","sha1"]}
   ]
   ```

## 4. 种子数据

`assets/commands_seed.json` 结构：

```json
{
  "version": 1,
  "aosp_head": "386fb904",
  "commands": [
    {
      "name": "add_hashtree_footer",
      "tab": "home",
      "groupTitle": "Hashtree",
      "title": "添加 Hashtree 页脚",
      "subtitle": "给镜像添加校验树页脚",
      "iconName": "Outline.AddCircleOutline",
      "sortOrder": 1,
      "priority": "P0",
      "paramsJson": null
    },
    {"name": "generate_rsa_key", "tab": "home", "groupTitle": "密钥管理", "title": "生成 RSA 密钥", "subtitle": "SHA256 密钥对", "iconName": "Outline.Key", "sortOrder": 1, "priority": "P0", "paramsJson": null},
    {"name": "create_vbmeta_image", "tab": "home", "groupTitle": "vbmeta", "title": "创建 vbmeta 镜像", "subtitle": "签名 vbmeta 头", "iconName": "Outline.Description", "sortOrder": 1, "priority": "P0", "paramsJson": null}
  ]
}
```

其余子命令同结构。总条数 ~30。

## 5. 缓存策略

- `fetchedAt` 超过 24h 重新拉 `--help`
- `aosp_head` 变化时强制全量刷新（`Application.onCreate` 里 check）
- Room 表 `commands` 用 `@Upsert`，天然幂等

## 6. 与旧 PrefData 的兼容

`PrefData.kt` 保留但不再作为 UI 数据源。作为兜底：若 Room 初始化失败（例如 SQLite 不可用），退回静态 `PrefData.homeGroups`。这个 fallback 分支在 M4 阶段做。
