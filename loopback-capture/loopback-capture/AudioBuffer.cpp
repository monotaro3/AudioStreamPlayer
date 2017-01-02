#include "common.h"
#include <iostream>

AudioBuffer &AudioBuffer::getInstance() {
	static AudioBuffer buffer;
	return buffer;
}

char* AudioBuffer::initBuffer(int deviceperiodms){
	devperiod = deviceperiodms;
	long leastdatamount = nSamplesPerSec * deviceperiodms / 1000 * wBitsPerSample / 8 * nChannels;
	bufferSize = 1;
	while (bufferSize < leastdatamount) { bufferSize = bufferSize * 2; }
	bufferSize = bufferSize * 2;
	mask = bufferSize - 1;
	try {
		ringBuffer = new char[bufferSize];
	}
	catch (std::bad_alloc) {
		printf("RingBuffer Allocation failed.");
		return 0;
	}
	bufStart = 0;
	datasize = 0;
	InitializeCriticalSection(&abcs);
	//bufEnd = 0;
	//readPointer = 0;
	return ringBuffer;
}

int AudioBuffer::write(char* data, long bytes) {
	if (bytes == 0) {
		return 0;
	}
	if (bytes >= bufferSize) {
		int n = memcpy_s(ringBuffer,bufferSize,(data+(bytes-bufferSize)),bufferSize);
		if (n == EINVAL || n == ERANGE)return 1;
		bufStart = 0;
		//bufEnd = bufferSize-1;
		datasize = bufferSize;
		//readPointer = 0;
	}
	else {
		//bufEnd = (bytes + bufEnd) & mask;
		//if (bufEnd > bufStart) {
		//}
		int tmpstart = (bufStart + datasize) & mask;
		if (bytes > bufferSize - tmpstart) {
			int n = memcpy_s((ringBuffer + tmpstart), bufferSize - tmpstart, data, bufferSize - tmpstart);
			if (n == EINVAL || n == ERANGE)return 1;
			n = memcpy_s(ringBuffer, tmpstart, (data+ bufferSize - tmpstart), bytes-(bufferSize - tmpstart));
			if (n == EINVAL || n == ERANGE)return 1;
			/*if (datasize + bytes > bufferSize) {
				datasize = bufferSize;
				bufStart = (tmpstart + bytes) & mask;
			}
			else {
				datasize += bytes;
			}*/
		}
		else {
			int n = memcpy_s((ringBuffer + tmpstart), bufferSize - tmpstart, data, bytes);
			if (n == EINVAL || n == ERANGE)return 1;
		}
		if (datasize + bytes > bufferSize) {
			datasize = bufferSize;
			bufStart = (tmpstart + bytes) & mask;
		}
		else {
			datasize += bytes;
		}
	}
	return 0;
}

int AudioBuffer::read(char* dst, long* bytes,int dstsize) {
	if (datasize == 0) {
		*bytes = 0;
		return 0;
	}
	if ((bufStart + datasize) > bufferSize) {
		int n = memcpy_s(dst, dstsize, ringBuffer + bufStart, bufferSize - bufStart);
		if (n == EINVAL || n == ERANGE)return 1;
		n = memcpy_s(dst+ bufferSize - bufStart,dstsize-(bufferSize - bufStart),ringBuffer,datasize- (bufferSize - bufStart));
		if (n == EINVAL || n == ERANGE)return 1;
		*bytes = datasize;
		datasize = 0;
	}
	else {
		int n = memcpy_s(dst, dstsize, ringBuffer + bufStart, datasize);
		if (n == EINVAL || n == ERANGE)return 1;
		*bytes = datasize;
		datasize = 0;
	}
	return 0;
}

LPVOID AudioBuffer::getCriticalSection() {
	return (LPVOID)&abcs;
}