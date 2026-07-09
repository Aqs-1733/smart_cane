idf_component_register(
    SRCS "main.c" "app_tasks.c"
    INCLUDE_DIRS "."
    REQUIRES
        common
        i2c_bus
        tof_sensors
        touch_input
        vibration_motor
        buzzer
        buttons
        gps_location
        risk_logic
        communication
)

