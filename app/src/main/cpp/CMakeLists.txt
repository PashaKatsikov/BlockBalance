# CMakeLists.txt for the per-project native "seasoning" library.
#
# Turned on with `gray.enableNativeStub = true` in gray.properties. When the
# flag is false the AGP externalNativeBuild block is skipped entirely and
# neither this file nor the produced .so ships.
#
# The library itself is small on purpose: a single deterministic byte-XOR
# routine plus a per-seed pattern constant. Its value to the build is not in
# what it does (which is nothing useful) but that clones scanners have to run
# NDK code through a much heavier analysis pipeline than DEX; a project with a
# unique .so is far harder to fingerprint by class hierarchy alone.
cmake_minimum_required(VERSION 3.22.1)

project(entropy_stub)

add_library(entropy_stub SHARED entropy_stub.cpp)

find_library(log-lib log)
target_link_libraries(entropy_stub ${log-lib})

target_compile_options(entropy_stub PRIVATE -O2 -fvisibility=hidden -fno-rtti -fno-exceptions)
