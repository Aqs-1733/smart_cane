idf_component_register(
    SRCS "tof_sensors.c"
    INCLUDE_DIRS "include"
    REQUIRES common i2c_bus esp_timer
)

