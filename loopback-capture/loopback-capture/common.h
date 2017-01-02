// common.h
#include "AudioStreamSocket.h" //It is necessary to include winsock2.h before windows.h
#include <stdio.h>
#include <windows.h>
#include <mmsystem.h>
#include <mmdeviceapi.h>
#include <audioclient.h>
#include <avrt.h>
#include <functiondiscoverykeys_devpkey.h>

#include "log.h"
#include "cleanup.h"
#include "prefs.h"
#include "loopback-capture.h"
#include "AudioBuffer.h"

