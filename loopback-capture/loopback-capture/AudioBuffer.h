#pragma once
#include <windows.h>
//#include <WinNT.h>

class AudioBuffer {
private:
	AudioBuffer() {};
	~AudioBuffer() {};
	AudioBuffer(const AudioBuffer &) {};
	AudioBuffer &operator=(const AudioBuffer &) { return *this; };
	char* ringBuffer;
	int bufferSize;
	int mask;
	int bufStart;
	//int bufEnd;
	int datasize;
	CRITICAL_SECTION abcs;
public:
	static AudioBuffer &getInstance();
	WORD  nChannels;
	DWORD nSamplesPerSec;
	WORD  wBitsPerSample;
	int devperiod;
	//deletePointer delBuffer;
	char* initBuffer(int deviceperiodms);
	//int readPointer;
	int write(char* data, long bytes);	
	int read(char* dst,long* bytes,int dstsize);
	int getBufferSize() { return bufferSize; };
	LPVOID getCriticalSection();

};

class deletePointer {
public:
	deletePointer() {};
	deletePointer(char* p):mP(p){};
	~deletePointer() {if(mP != nullptr)delete[] mP; };
	void setPointer(char * p) { mP = p; }
	char* mP;
};