#pragma once

#include <Arduino.h>

enum ButtonEventType {
  BUTTON_EVENT_CLICK = 0,
  BUTTON_EVENT_DOUBLE_CLICK,
  BUTTON_EVENT_LONG_PRESS
};

typedef void (*ButtonEventCallback)(ButtonEventType type);

void buttonsBegin();
void buttonsUpdate(ButtonEventCallback callback);
const char *buttonEventName(ButtonEventType type);
