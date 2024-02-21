// ITestService.aidl
package com.edllt.robloxmidi;

// Declare any non-default types here with import statements

interface ITestService {
    int getPid();
    int getUid();
    String UUID();
    String pianoKey(String isDown, int noteNumber);
    IBinder getFileSystemService();
}
