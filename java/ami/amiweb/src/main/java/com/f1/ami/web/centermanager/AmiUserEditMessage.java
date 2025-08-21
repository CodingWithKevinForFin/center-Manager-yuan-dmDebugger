package com.f1.ami.web.centermanager;

public class AmiUserEditMessage {
	//ALTER TABLE ADD ...
	public static final byte ACTION_TYPE_ADD_COLUMN = 1;
	
	//ALTER TABLE DROP ...
	public static final byte ACTION_TYPE_DROP_COLUMN = 2;
	
	//The below are considered updates to a column/////////
	//ALTER TABLE RENAME ...
	public static final byte ACTION_TYPE_RENAME_COLUMN = 3;
	
	//ALTER TABLE MODIFY ...
	public static final byte ACTION_TYPE_MODIFY_COLUMN = 4;
	
	//ALTER TABLE MOVE ...
	public static final byte ACTION_TYPE_MOVE_COLUMN = 5;
	///////////////////////////////////////////////////////
	
	
	//RENAME TABLE ...
	public static final byte ACTION_TYPE_RENAME_TABLE = 6;
	
	public static final byte ACTION_TYPE_WARNING = 7;
}
