idf_component_register(
    SRCS "touch_input.c"
    INCLUDE_DIRS "include"
    REQUIRES common i2c_bus esp_driver_gpio esp_timer
)
