cmake_minimum_required(VERSION 3.5)
project(a3_f4d0b_g0l0b)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(SOURCE_FILES
    cdup.h
    CSftp.c
    cwd.h
    dir.c
    dir.h
    usage.c
    usage.h
    user.h)

add_executable(a3_f4d0b_g0l0b ${SOURCE_FILES})