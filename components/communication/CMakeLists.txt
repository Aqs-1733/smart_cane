idf_component_register(
    SRCS "communication.c"
    INCLUDE_DIRS "include"
    REQUIRES
        common
        esp_wifi
        esp_event
        esp_netif
        esp_http_client
        esp_timer
        freertos
        nvs_flash
)
