#include "AudioStreamSocket.h"
#include <string>

AudioStreamSocket::AudioStreamSocket() :mPort(0), WsaStartupDone(false), ConnectSuccess(false){
}

AudioStreamSocket::~AudioStreamSocket() {
	if (WsaStartupDone) { 
		if(ConnectSuccess)closesocket(sock);
		WSACleanup();
	}
}

//for debug
union byteshort {
	short s;
	char c[2];
};

int AudioStreamSocket::initialize(int nPort,char* hostname,int sendexp2 = 11,int header = sizeof(int)){
	mPort = nPort;
	WSADATA wsaData;
	struct sockaddr_in server;
	//SOCKET sock;
	//char buf[2048];
	char *deststr;
	//unsigned int **addrptr;
	struct addrinfo hints, *res;
	//	struct in_addr addr;
	int err;
	sendsize_n = sendexp2;
	sendsize = 1;
	for (int i = 0; i < sendsize_n; i++) {
		sendsize = sendsize * 2;
	}
	headerSize = header;
	dataSize = sendsize - headerSize;
	sendBuffer = new char[sendsize];
	delsendBuffer.setPointer(sendBuffer);

	deststr = hostname;// "localhost"; //Use ADB forward

	if (WSAStartup(MAKEWORD(2, 0), &wsaData) != 0) {
		printf("WSAStartup failed\n");
		return 1;
	}
	WsaStartupDone = true;

	sock = socket(AF_INET, SOCK_STREAM, 0);
	if (sock == INVALID_SOCKET) {
		printf("socket : %d\n", WSAGetLastError());
		return 1;
	}

	server.sin_family = AF_INET;
	server.sin_port = htons((unsigned short)mPort);

	/*sock = socket(AF_INET, SOCK_STREAM, 0);
	if (sock == INVALID_SOCKET) {
		printf("socket : %d\n", WSAGetLastError());
		return 1;
	}

	server.sin_family = AF_INET;
	server.sin_port = htons(12345);
	*/
	if (inet_pton(AF_INET, deststr, &(server.sin_addr)) != 1) {
		memset(&hints, 0, sizeof(hints));
		hints.ai_socktype = SOCK_STREAM;
		hints.ai_family = AF_INET;

		if ((err = getaddrinfo(deststr, NULL, &hints, &res)) != 0) {
			printf("getaddrinfo error %d\n", err);
			return 1;
		}

		while (res != nullptr) {//*addrptr != NULL) {
			/*if (res->ai_addr == nullptr) {
				res++;
				continue;
			}*/
			server.sin_addr.S_un.S_addr = ((struct sockaddr_in *)(res->ai_addr))->sin_addr.S_un.S_addr;//*(*addrptr);

																									   // connect()が成功したらloopを抜けます
			if (connect(sock,
				(struct sockaddr *)&server,
				sizeof(server)) == 0) {
				break;
			}

			res = res->ai_next;//addrptr++;
				  // connectが失敗したら次のアドレスで試します
		}

		// connectが全て失敗した場合
		if (res == nullptr) {
			printf("connect error: %d\n", WSAGetLastError());
			return 1;
		}
	}
	else {
		// inet_addr()が成功したとき

		// connectが失敗したらエラーを表示して終了
		if (connect(sock,
			(struct sockaddr *)&server,
			sizeof(server)) != 0) {
			printf("connect error: %d\n", WSAGetLastError());
			return 1;
		}
	}
	ConnectSuccess = true;
	printf("Connect succeeded.\n");
	Sleep(50); //Wait until Android socket is ready. This is emprical value.
	AudioBuffer &audioBuffer = AudioBuffer::getInstance();
	printf("Channel       :%u\n", audioBuffer.nChannels);
	printf("nSamplesPerSec:%u\n", audioBuffer.nSamplesPerSec);
	printf("wBitsPerSample:%u\n", audioBuffer.wBitsPerSample);


	char initstream[16];
	memset(initstream, 0, sizeof(initstream));
	//confirm data transfer is available
	int n = recv(sock, initstream, 5, 0);
	if (n < 0) {
		printf("error while receiving \"ready\": %d\n", WSAGetLastError());
		return 1;
	}
	if (strcmp(initstream, "Ready")==0) {
		printf("received \"ready\".\n");
	}else{
		printf("could'nt receive \"ready\".\n");
		return 1;
	}
	//send MixFormat indformation
	memset(initstream, 0, sizeof(initstream));
	memcpy_s(initstream, sizeof(audioBuffer.nSamplesPerSec), &(audioBuffer.nSamplesPerSec), sizeof(audioBuffer.nSamplesPerSec));
	memcpy_s((initstream+4), sizeof(audioBuffer.nChannels), &(audioBuffer.nChannels), sizeof(audioBuffer.nChannels));
	memcpy_s((initstream+6), sizeof(audioBuffer.wBitsPerSample), &(audioBuffer.wBitsPerSample), sizeof(audioBuffer.wBitsPerSample));
	memcpy_s((initstream + 8), sizeof(audioBuffer.devperiod), &(audioBuffer.devperiod), sizeof(audioBuffer.devperiod));
	memcpy_s((initstream + 12), sizeof(sendsize), &(sendsize), sizeof(sendsize));
	int sendsum = 0;
	while (1) {
		n = send(sock, (initstream + sendsum), sizeof(initstream) - sendsum, 0);
		if (n < 0) {
			printf("MixFormatSendError : %d\n", WSAGetLastError());
			return 1;
		}
		sendsum += n;
		if (sendsum == sizeof(initstream))break;

	}
	//wait until Audiotrack on Android is ready
	memset(initstream, 0, sizeof(initstream));
	n = recv(sock, initstream, 5, 0);
	if (n < 0) {
		printf("recv : %d\n", WSAGetLastError());
		return 1;
	}
	if (strcmp(initstream, "Ready")==0) {
		printf("Audiotrack is now ready.\n");
	}
	else {
		printf("could'nt receive \"ready\" after sending MixFormat.\n");
		return 1;
	}

	//memset(buf, 0, sizeof(buf));
	return 0;
}

int AudioStreamSocket::sendstream(char* src, long sendbytes) {
	int n;
	int fullpacket_num = sendbytes >> sendsize_n;
	int mask = sendsize - 1;
	int fraction = sendbytes & mask;
	fraction += fullpacket_num * headerSize;
	if (fraction >= dataSize) {
		fraction -= dataSize;
		fullpacket_num++;
	}
	for (int i = 0; i < fullpacket_num + 1; i++) {
		int p_datasize;
		if (i == fullpacket_num) {
			if (fraction != 0) {
				p_datasize = fraction;
			}
			else {
				continue;
			}
		}
		else {
			p_datasize = dataSize;
		}
		n = memcpy_s(sendBuffer, headerSize, &((int)p_datasize), headerSize);
		if (n == EINVAL || n == ERANGE) { 
			printf("writing header to sendbuffer error\n");
			return 1;
		}
		n = memcpy_s(sendBuffer + headerSize, dataSize, src + dataSize*i, p_datasize);
		if (n == EINVAL || n == ERANGE) {
			printf("writing data to sendbuffer error\n");
			return 1;
		}

		int sendsum = 0;
		while (1) {
			n = send(sock, (sendBuffer + sendsum), sendsize - sendsum, 0);
			if (n < 0) {
				printf("AudioStreamSendError : %d\n", WSAGetLastError());
				return 1;
			}
			sendsum += n;
			if (sendsum == sendsize)break;
		}
		//debug
		/*union byteshort test;
		for (i = 0; i < 10; i++) {
			test.c[0] = sendBuffer[i * 2];
			test.c[1] = sendBuffer[i * 2 + 1];
			printf("%d,",test.s);
		}
		printf("\n");
		i = 0;*/
	}
	return 0;
}

int SendAudioStream(
	int mPort,
	char* hostname,
	HANDLE hSocketReady,
	HANDLE hStopEvent,
	HANDLE hWasapiReady
);

DWORD WINAPI AudioStreamSocketFunction(LPVOID pContext) {
	AudioStreamSocketArguments *pArgs =
		(AudioStreamSocketArguments*)pContext;
	pArgs->result = SendAudioStream(
		pArgs->mPort,
		pArgs->hostname,
		pArgs->hSocketReady,
		pArgs->hStopEvent,
		pArgs->hWasapiReady
	);
	return 0;
}

int SendAudioStream(
	int mPort,
	char* hostname,
	HANDLE hSocketReady,
	HANDLE hStopEvent,
	HANDLE hWasapiReady
) {
	//wait until audioformat is ready
	DWORD dwWaitResult = WaitForSingleObject(hWasapiReady, INFINITE);
	if (WAIT_OBJECT_0 != dwWaitResult) {
		printf("WaitForSingleObject(hWasapiReady) returned unexpected result 0x%08x, last error is %d", dwWaitResult, GetLastError());
		return 1;
	}
	//ResetEvent(hWasapiReady);
	
	AudioStreamSocket audiosocket = AudioStreamSocket();
	int initsocket = audiosocket.initialize(mPort, hostname);  //specify tcp port and host name
	if (initsocket == 1) {
		printf("Socket Initialization Failed.\n");
		return 1;
	}
	SetEvent(hSocketReady);
	//data transfer is ready
	
	bool bDone = false;
	HANDLE waitArray[2] = { hWasapiReady,hStopEvent };
	AudioBuffer &audioBuffer = AudioBuffer::getInstance();
	int buffersize = audioBuffer.getBufferSize();
	char *buffer = new char[buffersize];
	deletePointer delBuffer(buffer);
	long readsize;
	LPVOID abcs = audioBuffer.getCriticalSection();

	//short* debug = new short[2000];
	Sleep(100);

	while (!bDone) {
		//wait until first writing to buffer
		dwWaitResult = WaitForMultipleObjects(
			ARRAYSIZE(waitArray), waitArray,
			FALSE, INFINITE
		);
		if (WAIT_OBJECT_0 + 1 == dwWaitResult) {
			bDone = true;
			continue;
		}else if (WAIT_OBJECT_0 != dwWaitResult) {
			printf("WaitForSingleObject(WaitWriting) returned unexpected result 0x%08x, last error is %d", dwWaitResult, GetLastError());
			return 1;
		}
		//ResetEvent(hWasapiReady);
		
		//ResetEvent(hSocketReady);
		EnterCriticalSection((LPCRITICAL_SECTION)abcs);
		int n = audioBuffer.read(buffer, &readsize,buffersize);
		if (n == 1) {
			printf("Reading from audiobuffer failed.\n");
			return 1;
		}
		LeaveCriticalSection((LPCRITICAL_SECTION)abcs);
		//SetEvent(hSocketReady);
		//debug
		//memcpy_s(debug, 0, buffer, buffersize);

		if (readsize > 0) {
			n = audiosocket.sendstream(buffer, readsize);
			if (n == 1) {
				printf("Sending audiostream failed.\n");
				return 1;
			}
		}
	}
	/*
	//debug
	extern HMMIO hFilefordebug;
	char recvback[4096];
	int endflag = 999999;
	memcpy_s(recvback, 4, &(endflag), 4);
	send(audiosocket.getsocket(), recvback, 2048, 0);
	for (int i = 0; i < 1024; i++) {
		int n = recv(audiosocket.getsocket(), recvback, 4096, 0);
		if (n < 0) {
			printf("recv : %d\n", WSAGetLastError());
			return 1;
		}
		LONG lBytesWritten = mmioWrite(hFilefordebug, recvback, 4096);
		if (4096 != lBytesWritten) {
			printf("mmioWrite wrote %u bytes : expected %u bytes", lBytesWritten, 4096);
			return 1;
		}
	}
	//__debug
	*/

	return 0;
}