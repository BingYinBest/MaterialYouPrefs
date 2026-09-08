# AVB FEC 支持

## 上游事实
AOSP `avbtool.py` 中：
- `--do_not_generate_fec`：关闭 FEC
- `--fec_num_roots`：设置 FEC 根节点数
- `ImageDescriptor` 结构：`fec_num_roots / fec_offset / fec_size`
- Hashtree 后处理时调用 `fec_rs` 二进制做编码

## 实现策略
在 Android 上，用**原生 `libavbfec.so`** 替代 AOSP `fec_rs` 二进制：

1. 从 AOSP `external/avb/tools/fec/fec_rs.c` 抽取核心算法
2. NDK 交叉编译成 `libavbfec.so`
3. `avbtool.py` 里的 `subprocess.run(['fec_rs', ...])` 用 `ctypes.CDLL('libavbfec.so')` 替换
4. 调用返回码 / 内存 buffer 与 CLI 语义对齐

## 许可证
- `fec_rs.c` 是 LGPL-2.1
- `libavbfec.so` 作为独立 so 存在，app 主代码不静态链接（动态加载），符合 LGPL 要求
- LICENSE 目录里放 `LGPL-2.1.txt`

## 参数
| 参数 | 默认 | 说明 |
|---|---|---|
| `--do_not_generate_fec` | 未设 | 关闭 FEC |
| `--fec_num_roots` | 0（=自动） | 根节点数量 |
| 块大小 | 4096 | avbtool 内部常量 |
