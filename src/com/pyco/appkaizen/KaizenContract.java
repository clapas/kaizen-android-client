package com.pyco.appkaizen;

import android.provider.BaseColumns;

public final class KaizenContract {
    // To prevent someone from accidentally instantiating the contract class,
    // give it an empty constructor.
    public KaizenContract() {}

    /* Inner class that defines the table contents */
    public static abstract class KaizenContact implements BaseColumns {
        public static final String TABLE_NAME = "contact";
        public static final String COLUMN_NAME_JID = "jid";
        public static final String COLUMN_NAME_FULLNAME = "full_name";
    }
    public static final String SQL_CREATE_CONTACTS =
        "CREATE TABLE " + KaizenContact.TABLE_NAME + " (" +
        KaizenContact._ID + " INTEGER PRIMARY KEY," +
        KaizenContact.COLUMN_NAME_JID + " TEXT, " +
        KaizenContact.COLUMN_NAME_FULLNAME + " TEXT)";
    
    public static final String SQL_DELETE_CONTACTS =
        "DROP TABLE IF EXISTS " + KaizenContact.TABLE_NAME;
}
