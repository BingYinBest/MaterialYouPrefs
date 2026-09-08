package com.bingyin.materialyouprefs.ui

// This file used to contain a M2.5-draft `HomeScreen` composable that
// referenced `AppState.homeRepository` (a field we never added).
// The correct implementation lives in `ui/screens/HomeScreen.kt`.
//
// We keep the file with only the package declaration so git still tracks
// this path but there is no `HomeScreen` overload from this package.
// Without this cleanup the compiler reports:
//   e: ui/HomeScreen.kt:31 Conflicting overloads: fun HomeScreen(...): Unit
//   e: ui/screens/HomeScreen.kt:32 Conflicting overloads
//   e: ui/MaterialYouPrefsApp.kt:89 Cannot infer type for this parameter
//     (itemId parameter — compiler couldn't decide which HomeScreen
//      overload it was binding against).
