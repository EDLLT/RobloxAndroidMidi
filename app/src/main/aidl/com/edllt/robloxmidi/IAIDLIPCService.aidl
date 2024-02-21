// IAIDLIPCService.aidl
package com.edllt.robloxmidi;

// Declare any non-default types here with import statements

interface IAIDLIPCService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
//    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
//            double aDouble, String aString);
    String pianoKey(int x, int y, int contact, String isDown);
}