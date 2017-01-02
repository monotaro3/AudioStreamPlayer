//#pragma once
#ifndef INCLUDE_AUDIOSTREAMSOCKET_H
#define INCLUDE_AUDIOSTREAMSOCKET_H
#include <stdio.h>
#include <winsock2.h>
#include <Ws2tcpip.h>
#include <mmsystem.h>
#include <Windows.h>
#include "AudioBuffer.h"
#pragma comment (lib, "winmm.lib")
#pragma comment (lib, "ws2_32.lib")



class AudioStreamSocket {
public:
	AudioStreamSocket() ;
	~AudioStreamSocket();
	int initialize(int nPort,char* hostname,int n,int header);
	int sendstream(char* src, long sendbytes);
	SOCKET getsocket() { return sock; };
private:
	int mPort;
	SOCKET sock;
	bool WsaStartupDone;
	bool ConnectSuccess;
	int sendsize_n;
	int sendsize;
	int headerSize;
	int dataSize;
	char* sendBuffer;
	deletePointer delsendBuffer;
};

struct AudioStreamSocketArguments {
	int result;
	int mPort;
	char* hostname;
	HANDLE hStopEvent;
	HANDLE hSocketReady;
	HANDLE hWasapiReady;
};

DWORD WINAPI AudioStreamSocketFunction(LPVOID pContext);

#endif //