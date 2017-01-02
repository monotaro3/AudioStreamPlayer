// main.cpp

#include "common.h"
#include "AudioStreamSocket.h"

//debug
HMMIO hFilefordebug;

int do_everything(int argc, LPCWSTR argv[]);

int _cdecl wmain(int argc, LPCWSTR argv[]) {
    HRESULT hr = S_OK;

    hr = CoInitialize(NULL);
    if (FAILED(hr)) {
        ERR(L"CoInitialize failed: hr = 0x%08x", hr);
        return -__LINE__;
    }
    CoUninitializeOnExit cuoe;

    return do_everything(argc, argv);
}

int do_everything(int argc, LPCWSTR argv[]) {
    HRESULT hr = S_OK;

    // parse command line
    CPrefs prefs(argc, argv, hr);
    if (FAILED(hr)) {
        ERR(L"CPrefs::CPrefs constructor failed: hr = 0x%08x", hr);
        return -__LINE__;
    }
    if (S_FALSE == hr) {
        // nothing to do
        return 0;
    }

    // create a "loopback capture has started" event
    HANDLE hStartedEvent = CreateEvent(NULL, FALSE, FALSE, NULL);
    if (NULL == hStartedEvent) {
        ERR(L"CreateEvent failed: last error is %u", GetLastError());
        return -__LINE__;
    }
    CloseHandleOnExit closeStartedEvent(hStartedEvent);

    // create a "stop capturing now" event
    HANDLE hStopEvent = CreateEvent(NULL, TRUE, FALSE, NULL);
    if (NULL == hStopEvent) {
        ERR(L"CreateEvent failed: last error is %u", GetLastError());
        return -__LINE__;
    }
    CloseHandleOnExit closeStopEvent(hStopEvent);

	// create a "Socketready" event
	HANDLE hSocketReady = CreateEvent(NULL, TRUE, FALSE, NULL);
	if (NULL == hSocketReady) {
		ERR(L"CreateEvent failed: last error is %u", GetLastError());
		return -__LINE__;
	}
	CloseHandleOnExit closeSocketReady(hSocketReady);

	// create a "WasapiReady" event
	HANDLE hWasapiReady = CreateEvent(NULL, FALSE, FALSE, NULL);
	if (NULL == hWasapiReady) {
		ERR(L"CreateEvent failed: last error is %u", GetLastError());
		return -__LINE__;
	}
	CloseHandleOnExit closehWasapiReady(hWasapiReady);

    // create arguments for loopback capture thread
    LoopbackCaptureThreadFunctionArguments threadArgs;
    threadArgs.hr = E_UNEXPECTED; // thread will overwrite this
    threadArgs.pMMDevice = prefs.m_pMMDevice;
    threadArgs.bInt16 = prefs.m_bInt16;
    threadArgs.hFile = prefs.m_hFile;
    threadArgs.hStartedEvent = hStartedEvent;
    threadArgs.hStopEvent = hStopEvent;
	threadArgs.hSocketReady = hSocketReady;
	threadArgs.hWasapiReady = hWasapiReady;
    threadArgs.nFrames = 0;

	//debug
	hFilefordebug = prefs.m_hFile;

    HANDLE hThread = CreateThread(
        NULL, 0,
        LoopbackCaptureThreadFunction, &threadArgs,
        0, NULL
    );
    if (NULL == hThread) {
        ERR(L"CreateThread failed: last error is %u", GetLastError());
        return -__LINE__;
    }
    CloseHandleOnExit closeThread(hThread);

	/*
	//wait until audioformat is ready
	DWORD dwWaitResult = WaitForSingleObject(hWasapiReady, INFINITE);
	if (WAIT_OBJECT_0 != dwWaitResult) {
		ERR(L"WaitForSingleObject(hWasapiReady) returned unexpected result 0x%08x, last error is %d", dwWaitResult, GetLastError());
		return -__LINE__;
	}
	ResetEvent(hWasapiReady);
	*/
	
	//start socket connection
	AudioStreamSocketArguments ASSthreadArgs;
	ASSthreadArgs.result = 0;
	ASSthreadArgs.mPort = prefs.mPort;
	ASSthreadArgs.hostname = prefs.hostname;
	ASSthreadArgs.hSocketReady = hSocketReady;
	ASSthreadArgs.hStopEvent = hStopEvent;
	ASSthreadArgs.hWasapiReady = hWasapiReady;

	HANDLE hASSThread = CreateThread(
		NULL, 0,
		AudioStreamSocketFunction, &ASSthreadArgs,
		0, NULL
	);
	if (NULL == hASSThread) {
		ERR(L"CreateThread(ASS) failed: last error is %u", GetLastError());
		return -__LINE__;
	}
	CloseHandleOnExit closeASSThread(hASSThread);

	/*
	AudioStreamSocket audiosocket = AudioStreamSocket();
	int initsocket = audiosocket.initialize(12345,"localhost");  //specify tcp port and host name
	if (initsocket == 1) {
		ERR(L"Socket Initialization Failed.");
		return -__LINE__;
	}
	*/


    // wait for either capture to start or the thread to end
    HANDLE waitArray[3] = { hStartedEvent, hThread ,hASSThread};
    DWORD dwWaitResult = WaitForMultipleObjects(
        ARRAYSIZE(waitArray), waitArray,
        FALSE, INFINITE
    );

    if (WAIT_OBJECT_0 + 1 == dwWaitResult) {
        ERR(L"Thread aborted before starting to loopback capture: hr = 0x%08x", threadArgs.hr);
        return -__LINE__;
    }else if (WAIT_OBJECT_0 + 2 == dwWaitResult){
		ERR(L"Thread(AudioStreamSocket) aborted before starting to loopback capture");
		return -__LINE__;
	}else if (WAIT_OBJECT_0 != dwWaitResult) {
        ERR(L"Unexpected WaitForMultipleObjects return value %u", dwWaitResult);
        return -__LINE__;
    }

    // at this point capture is running
    // wait for the user to press a key or for capture to error out
    {
        WaitForSingleObjectOnExit waitForThread(hThread);
		WaitForSingleObjectOnExit waitForASSThread(hASSThread);
        SetEventOnExit setStopEvent(hStopEvent);
        HANDLE hStdIn = GetStdHandle(STD_INPUT_HANDLE);

        if (INVALID_HANDLE_VALUE == hStdIn) {
            ERR(L"GetStdHandle returned INVALID_HANDLE_VALUE: last error is %u", GetLastError());
            return -__LINE__;
        }

        LOG(L"%s", L"Press Enter to quit...");

        HANDLE rhHandles[3] = { hThread, hStdIn, hASSThread};

        bool bKeepWaiting = true;
        while (bKeepWaiting) {

            dwWaitResult = WaitForMultipleObjects(ARRAYSIZE(rhHandles), rhHandles, FALSE, INFINITE);

            switch (dwWaitResult) {

            case WAIT_OBJECT_0: // hThread
                ERR(L"%s", L"The thread terminated early - something bad happened");
                bKeepWaiting = false;
                break;

            case WAIT_OBJECT_0 + 1: // hStdIn
                // see if any of them was an Enter key-up event
                INPUT_RECORD rInput[128];
                DWORD nEvents;
                if (!ReadConsoleInput(hStdIn, rInput, ARRAYSIZE(rInput), &nEvents)) {
                    ERR(L"ReadConsoleInput failed: last error is %u", GetLastError());
                    bKeepWaiting = false;
                }
                else {
                    for (DWORD i = 0; i < nEvents; i++) {
                        if (
                            KEY_EVENT == rInput[i].EventType &&
                            VK_RETURN == rInput[i].Event.KeyEvent.wVirtualKeyCode &&
                            !rInput[i].Event.KeyEvent.bKeyDown
                            ) {
                            LOG(L"%s", L"Stopping capture...");
                            bKeepWaiting = false;
                            break;
                        }
                    }
                    // if none of them were Enter key-up events,
                    // continue waiting
                }
                break;

			case WAIT_OBJECT_0 + 2: // hASSThread
				ERR(L"%s", L"The thread(AudioStreamSocket) terminated early - something bad happened");
				bKeepWaiting = false;
				break;

            default:
                ERR(L"WaitForMultipleObjects returned unexpected value 0x%08x", dwWaitResult);
                bKeepWaiting = false;
                break;
            } // switch
        } // while
    } // naked scope
	AudioBuffer &audioBuffer = AudioBuffer::getInstance();
	LPVOID abcs = audioBuffer.getCriticalSection();
	DeleteCriticalSection((LPCRITICAL_SECTION)abcs);

    // at this point the thread is definitely finished

    DWORD exitCode;
    if (!GetExitCodeThread(hThread, &exitCode)) {
        ERR(L"GetExitCodeThread failed: last error is %u", GetLastError());
        return -__LINE__;
    }

    if (0 != exitCode) {
        ERR(L"Loopback capture thread exit code is %u; expected 0", exitCode);
        return -__LINE__;
    }

    if (S_OK != threadArgs.hr) {
        ERR(L"Thread HRESULT is 0x%08x", threadArgs.hr);
        return -__LINE__;
    }

	/*
    // everything went well... fixup the fact chunk in the file
    MMRESULT result = mmioClose(prefs.m_hFile, 0);
    prefs.m_hFile = NULL;
    if (MMSYSERR_NOERROR != result) {
        ERR(L"mmioClose failed: MMSYSERR = %u", result);
        return -__LINE__;
    }

    // reopen the file in read/write mode
    MMIOINFO mi = {0};
    prefs.m_hFile = mmioOpen(const_cast<LPWSTR>(prefs.m_szFilename), &mi, MMIO_READWRITE);
    if (NULL == prefs.m_hFile) {
        ERR(L"mmioOpen(\"%ls\", ...) failed. wErrorRet == %u", prefs.m_szFilename, mi.wErrorRet);
        return -__LINE__;
    }

    // descend into the RIFF/WAVE chunk
    MMCKINFO ckRIFF = {0};
    ckRIFF.ckid = MAKEFOURCC('W', 'A', 'V', 'E'); // this is right for mmioDescend
    result = mmioDescend(prefs.m_hFile, &ckRIFF, NULL, MMIO_FINDRIFF);
    if (MMSYSERR_NOERROR != result) {
        ERR(L"mmioDescend(\"WAVE\") failed: MMSYSERR = %u", result);
        return -__LINE__;
    }

    // descend into the fact chunk
    MMCKINFO ckFact = {0};
    ckFact.ckid = MAKEFOURCC('f', 'a', 'c', 't');
    result = mmioDescend(prefs.m_hFile, &ckFact, &ckRIFF, MMIO_FINDCHUNK);
    if (MMSYSERR_NOERROR != result) {
        ERR(L"mmioDescend(\"fact\") failed: MMSYSERR = %u", result);
        return -__LINE__;
    }

    // write the correct data to the fact chunk
    LONG lBytesWritten = mmioWrite(
        prefs.m_hFile,
        reinterpret_cast<PCHAR>(&threadArgs.nFrames),
        sizeof(threadArgs.nFrames)
    );
    if (lBytesWritten != sizeof(threadArgs.nFrames)) {
        ERR(L"Updating the fact chunk wrote %u bytes; expected %u", lBytesWritten, (UINT32)sizeof(threadArgs.nFrames));
        return -__LINE__;
    }

    // ascend out of the fact chunk
    result = mmioAscend(prefs.m_hFile, &ckFact, 0);
    if (MMSYSERR_NOERROR != result) {
        ERR(L"mmioAscend(\"fact\") failed: MMSYSERR = %u", result);
        return -__LINE__;
    }
	*/

    // let prefs' destructor call mmioClose
    
    return 0;
}
