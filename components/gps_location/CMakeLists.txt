idf_component_register(
    SRCS "gps_location.c"
    INCLUDE_DIRS "include"
    REQUIRES common esp_driver_gpio esp_driver_uart esp_timer
)
