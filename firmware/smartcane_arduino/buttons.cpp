#include "buttons.h"

#include "config.h"

static bool stablePressed = false;
static bool lastRawPressed = false;
static bool longSent = false;
static bool clickPending = false;
static unsigned long changedAtMs = 0;
static unsigned long pressedAtMs = 0;
static unsigned long lastClickMs = 0;

static bool readPressed() {
  int raw = digitalRead(SMARTCANE_SOS_BUTTON_PIN);
  return SMARTCANE_SOS_ACTIVE_LOW ? (raw == LOW) : (raw == HIGH);
}

void buttonsBegin() {
  pinMode(SMARTCANE_SOS_BUTTON_PIN, SMARTCANE_SOS_ACTIVE_LOW ? INPUT_PULLUP : INPUT_PULLDOWN);
  stablePressed = readPressed();
  lastRawPressed = stablePressed;
  changedAtMs = millis();
  Serial.print(F("[SOS] button GPIO "));
  Serial.println(SMARTCANE_SOS_BUTTON_PIN);
}

static void sendEvent(ButtonEventCallback callback, ButtonEventType type) {
  if (callback != nullptr) {
    callback(type);
  }
}

void buttonsUpdate(ButtonEventCallback callback) {
  bool rawPressed = readPressed();
  unsigned long now = millis();

  if (rawPressed != lastRawPressed) {
    lastRawPressed = rawPressed;
    changedAtMs = now;
  }

  if (now - changedAtMs < SMARTCANE_BUTTON_DEBOUNCE_MS) {
    return;
  }

  if (rawPressed != stablePressed) {
    stablePressed = rawPressed;
    if (stablePressed) {
      pressedAtMs = now;
      longSent = false;
      Serial.println(F("[SOS] press"));
    } else {
      Serial.println(F("[SOS] release"));
      if (!longSent) {
        if (clickPending && now - lastClickMs <= SMARTCANE_BUTTON_DOUBLE_CLICK_MS) {
          clickPending = false;
          sendEvent(callback, BUTTON_EVENT_DOUBLE_CLICK);
        } else {
          clickPending = true;
          lastClickMs = now;
        }
      }
    }
  }

  if (stablePressed && !longSent && now - pressedAtMs >= SMARTCANE_SOS_HOLD_MS) {
    longSent = true;
    clickPending = false;
    sendEvent(callback, BUTTON_EVENT_LONG_PRESS);
  }

  if (clickPending && now - lastClickMs > SMARTCANE_BUTTON_DOUBLE_CLICK_MS) {
    clickPending = false;
    sendEvent(callback, BUTTON_EVENT_CLICK);
  }
}

const char *buttonEventName(ButtonEventType type) {
  switch (type) {
    case BUTTON_EVENT_DOUBLE_CLICK:
      return "double_click";
    case BUTTON_EVENT_LONG_PRESS:
      return "long_press";
    case BUTTON_EVENT_CLICK:
    default:
      return "click";
  }
}
