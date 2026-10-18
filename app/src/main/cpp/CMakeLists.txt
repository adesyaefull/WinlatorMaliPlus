cmake_minimum_required(VERSION 3.22.1)

project(Winlator)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

add_subdirectory(OpenXR-SDK)
add_subdirectory(patchelf)
add_subdirectory(adrenotools)
add_subdirectory(thirdparty/dxbc)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add Winlator shared library
include_directories(winlator/include)

add_library(winlator SHARED
        xr/engine.c
        xr/framebuffer.c
        xr/input.c
        xr/main.c
        xr/math.c
        xr/renderer.c
        winlator/drawable.c
        winlator/gpu_image.c
        winlator/gpu_helper.c
        winlator/sysvshared_memory.c
        winlator/xconnector_epoll.c
        winlator/alsa_client.c
        winlator/vulkan.c
        winlator/arrays.c
        winlator/ring_buffer.c
        winlator/xinput_stream.c
        winlator/xoutput_stream.c
        winlator/renderer/egl.cpp
        winlator/renderer/renderer_jni.cpp
        winlator/renderer/shader.cpp
        winlator/renderer/window.cpp
        winlator/renderer/cursor.cpp
        winlator/renderer/xform.cpp
        winlator/renderer/view_transformation.cpp
        winlator/renderer/blit_converter.cpp
        winlator/renderer/displayx.cpp
        winlator/rambooster.cpp
        apex/apex_pacing.cpp
        apex/apex_pipeline.cpp
        apex/apex_jni_bridge.cpp
)

target_compile_options(winlator PRIVATE -Wall -Wextra)

target_link_libraries(winlator
        log
        android
        jnigraphics
        openxr_loader
        aaudio
        EGL
        GLESv2
        GLESv3
        adrenotools
)

add_library(vulkan_renderer SHARED
        winlator/vulkan_jni.cpp
        winlator/VulkanRendererContext.cpp
        winlator/VulkanRendererScanout.cpp
        winlator/lsfg/lsfg_probe.cpp
        winlator/lsfg/lsfg_dll.cpp
        winlator/lsfg/lsfg_dxbc.cpp
        winlator/lsfg/lsfg_vkd.cpp
        winlator/lsfg/lsfg_common.cpp
        winlator/lsfg/lsfg_shaders.cpp
        winlator/lsfg/lsfg_mipmaps.cpp
        winlator/lsfg/lsfg_alpha.cpp
        winlator/lsfg/lsfg_beta.cpp
        winlator/lsfg/lsfg_gamma.cpp
        winlator/lsfg/lsfg_delta.cpp
        winlator/lsfg/lsfg_generate.cpp
        winlator/lsfg/lsfg_chain.cpp
        winlator/lsfg/lsfg_pacer.cpp
        winlator/lsfg/lsfg_engine.cpp
        winlator/lsfg/lsfg_governor.cpp
        winlator/lsfg/lsfg_jni.cpp
        winlator/winfg/framegen.cpp
        winlator/winfg/winfg_vkd.cpp
        winlator/winfg/winfg_engine.cpp
        scanout/ScanoutContext.cpp
)

target_compile_definitions(vulkan_renderer PRIVATE
        WINFG_UPSTREAM="v0.3.0"
)

target_compile_options(vulkan_renderer PRIVATE
        -Wall -Wextra -fvisibility=hidden -std=c++17
)

target_link_libraries(vulkan_renderer
        log
        android
        vulkan
        adrenotools
        dxbc
        dl
        atomic
)

# Add Fake Input library for controller emulation (loaded via LD_PRELOAD)
add_library(fakeinput SHARED
        winlator/fakeinput.cpp
)

target_compile_options(fakeinput PRIVATE -Wall -Wextra -fvisibility=hidden)

target_link_libraries(fakeinput
        log
        dl
)

add_subdirectory(gladiorenderer)

