cmake_minimum_required(VERSION 2.8)
SET(CMAKE_CXX_FLAGS_RELEASE "-Wall -O2")
# デバッグビルド用オプション
SET(CMAKE_CXX_FLAGS_RELEASE "-g")
# ビルドのタイプはリリース
SET(CMAKE_BUILD_TYPE Release)

PROJECT(dio CXX)
# バージョン番号
SET(serial "1.0.0")
# 共有ライブラリのバージョン番号
SET(soserial "1")

# 共有ライブラリとしてビルド
ADD_LIBRARY(dio SHARED dio.cpp)
ADD_EXECUTABLE(dioexe dio.cpp)
#TARGET_LINK_LIBRARIES(dioexe Dio)