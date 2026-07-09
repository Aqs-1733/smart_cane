idf_component_register(
    SRCS "vibration_motor.c"
    INCLUDE_DIRS "include"
    REQUIRES common i2c_bus esp_timer
)

