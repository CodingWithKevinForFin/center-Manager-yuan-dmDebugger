package com.f1.ami.web.centermanager.editors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



import com.f1.ami.amicommon.AmiConsts;
import com.f1.ami.amicommon.AmiUtils;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebFormatterManager;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.AmiUserEditMessage;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Table;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerAbstractEditCenterObjectPortlet;
import com.f1.base.Action;
import com.f1.base.Column;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.fastwebcolumns.FastWebColumns;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenuLink;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.PortletManager;
import com.f1.suite.web.portal.impl.DividerPortlet;
import com.f1.suite.web.portal.impl.FastTableEditListener;
import com.f1.suite.web.portal.impl.FastTablePortlet;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.RootPortlet;
import com.f1.suite.web.portal.impl.WebColumnEditConfig;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletCheckboxField;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.portal.impl.form.FormPortletNumericRangeField;
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.suite.web.table.WebCellFormatter;
import com.f1.suite.web.table.WebColumn;
import com.f1.suite.web.table.WebContextMenuFactory;
import com.f1.suite.web.table.WebContextMenuListener;
import com.f1.suite.web.table.WebTable;
import com.f1.suite.web.table.fast.FastWebTable;
import com.f1.suite.web.table.impl.MapWebCellFormatter;
import com.f1.suite.web.table.impl.NumberWebCellFormatter;
import com.f1.suite.web.table.impl.WebCellStyleWrapperFormatter;
import com.f1.utils.CH;
import com.f1.utils.MH;
import com.f1.utils.OH;
import com.f1.utils.SH;
import com.f1.utils.casters.Caster_Boolean;
import com.f1.utils.casters.Caster_String;
import com.f1.utils.concurrent.HasherMap;
import com.f1.utils.concurrent.IdentityHashSet;
import com.f1.utils.formatter.BasicTextFormatter;
import com.f1.utils.impl.CaseInsensitiveHasher;
import com.f1.utils.string.ExpressionParserException;
import com.f1.utils.string.sqlnode.CreateTableNode;
import com.f1.utils.structs.MapInMap;
import com.f1.utils.structs.Tuple2;
import com.f1.utils.structs.table.BasicTable;
import com.f1.utils.structs.table.SmartTable;

/*
 * To figure out the minimal path to get from the original schema to the edited schema, a naive schema diffing will not work: Think of this scneario where, 
 * 
 * Original schema: (a,b,c,d)
 * First delete column c: -> (a,b,d)
 * Then rename d to c : -> (a,b,c)
 * A pure schema diffing will result in -> "DROP COLUMN d".
 * But in reality it should be: "DROP COLUMN C, RENAME D TO C"
 */

public class AmiCenterManagerEditColumnPortlet extends AmiCenterManagerAbstractEditCenterObjectPortlet
		implements WebContextMenuListener, WebContextMenuFactory, FastTableEditListener {
	private static final String BG_GREY = "_bg=#4c4c4c";
	private static final int LEFTPOS = 120;
	private static final int TOPPOS = 20;
	
	public static final String KEY_COLUMN_DATATYPE = "type";
	public static final String KEY_COLUMN_OPTIONS = "options";
	public static final String KEY_COLUMN_POS = "position";
	
	public static final Set<Character> RESERVED_COLUMN_NAMES = CH.s(AmiConsts.RESERVED_PARAM_AMIID, AmiConsts.RESERVED_PARAM_MODIFIED_ON, AmiConsts.RESERVED_PARAM_NOW, AmiConsts.RESERVED_PARAM_CREATED_ON, AmiConsts.RESERVED_PARAM_REVISION, AmiConsts.RESERVED_PARAM_TYPE, AmiConsts.RESERVED_PARAM_ID, AmiConsts.RESERVED_PARAM_EXPIRED, AmiConsts.RESERVED_PARAM_APPLICATION);
	public static final byte ADD_RESERVED_COLUMN_DFLT = 0;
	public static final byte ADD_RESERVED_COLUMN_BEFORE = 1;
	public static final byte ADD_RESERVED_COLUMN_AFTER = 2;
	
	
	
	final private AmiWebService service;
	final private FormPortlet tableInfoPortlet;
	final private FormPortletTextField tableNameField;
	final private FormPortletSelectField<String> tablePersistEngineField;
	final private FormPortletCheckboxField tableBroadCastField;
	final private FormPortletTextField tableRefreshPeriodMsField;
	final private FormPortletSelectField<String> tableOnUndefColumnField;
	final private FormPortletTextField tableInitialCapacityField;
	final private FastTablePortlet columnMetadata;
	final private FastTablePortlet userLogTable;
	private boolean enableColumnEditing = false;
	private HasherMap<String, TableEditableColumn> editableColumnIds = new HasherMap<String, TableEditableColumn>();
	private HashMap<String, Row> colNames2rows_Table = new HashMap<String, Row>();
	
	//only track add and delete
	private HashMap<String, Row> colNames2rows_Log = new HashMap<String, Row>();
	private Row renameTableLogRow = null;
	private Set<String> existingColNames = new HashSet<String>();
	final private AmiCenterManagerColumnMetaDataEditForm columnMetaDataEditForm;
	
	private String sql;
	private Map<String, String> origConfig; 
	private MapInMap<String,String,String> origColumnConfig = new MapInMap<String,String,String>();
	
	private Set<String> origColNames = new HashSet<String>();
	private Set<LinkedList<String>> editChains = new IdentityHashSet<LinkedList<String>>();
	private ArrayList<String> curColumns = new ArrayList<String>();

	public AmiCenterManagerEditColumnPortlet(PortletConfig config, boolean isAdd) {
		super(config, isAdd);
		this.service = AmiWebUtils.getService(getManager());
		PortletManager manager = service.getPortletManager();
		tableInfoPortlet = new FormPortlet(manager.generateConfig());
		//ROW1
		tableNameField = tableInfoPortlet.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("Table Name")));
		tableNameField.setLeftPosPx(LEFTPOS).setTopPosPx(TOPPOS).setWidthPx(300).setHeightPx(DEFAULT_ROWHEIGHT);
		if (!isAdd) {
			this.tableInfoPortlet.addField(enableEditingCheckbox);
			enableEditingCheckbox.setLeftPosPx(LEFTPOS + 400).setTopPosPx(TOPPOS).setWidthPx(20).setHeightPx(20);
		}
		//ROW2
		tablePersistEngineField = tableInfoPortlet.addField(new FormPortletSelectField<String>(String.class, "PersistEngine"));
		tablePersistEngineField.addOption(AmiCenterEntityConsts.PERSIST_ENGINE_TYPE_NONE, AmiCenterEntityConsts.PERSIST_ENGINE_TYPE_NONE);
		tablePersistEngineField.addOption(AmiCenterEntityConsts.PERSIST_ENGINE_TYPE_FAST, AmiCenterEntityConsts.PERSIST_ENGINE_TYPE_FAST);
		tablePersistEngineField.addOption(AmiCenterEntityConsts.PERSIST_ENGINE_TYPE_HISTORICAL, AmiCenterEntityConsts.PERSIST_ENGINE_TYPE_HISTORICAL);
		tablePersistEngineField.addOption(AmiCenterEntityConsts.PERSIST_ENGINE_TYPE_TEXT, AmiCenterEntityConsts.PERSIST_ENGINE_TYPE_TEXT);
		tablePersistEngineField.setLeftPosPx(LEFTPOS).setTopPosPx(TOPPOS + 40).setWidthPx(100).setHeightPx(DEFAULT_ROWHEIGHT);

		tableBroadCastField = tableInfoPortlet.addField(new FormPortletCheckboxField("Broadcast"));
		tableBroadCastField.setLeftPosPx(LEFTPOS + 200).setTopPosPx(TOPPOS + 40).setWidthPx(20).setHeightPx(20);

		//ROW3
		tableRefreshPeriodMsField = tableInfoPortlet.addField(new FormPortletTextField("RefreshPeriodMs"));
		tableRefreshPeriodMsField.setLeftPosPx(LEFTPOS).setTopPosPx(TOPPOS + 80).setWidthPx(150).setHeightPx(DEFAULT_ROWHEIGHT);

		tableOnUndefColumnField = tableInfoPortlet.addField(new FormPortletSelectField<String>(String.class, "OnUndefColumn"));
		tableOnUndefColumnField.addOption(AmiCenterEntityConsts.ON_UNDEF_COLUMN_OPTION_REJECT, AmiCenterEntityConsts.ON_UNDEF_COLUMN_OPTION_REJECT);
		tableOnUndefColumnField.addOption(AmiCenterEntityConsts.ON_UNDEF_COLUMN_OPTION_IGNORE, AmiCenterEntityConsts.ON_UNDEF_COLUMN_OPTION_IGNORE);
		tableOnUndefColumnField.addOption(AmiCenterEntityConsts.ON_UNDEF_COLUMN_OPTION_ADD, AmiCenterEntityConsts.ON_UNDEF_COLUMN_OPTION_ADD);
		tableOnUndefColumnField.setLeftPosPx(LEFTPOS + 270).setTopPosPx(TOPPOS + 80).setWidthPx(150).setHeightPx(DEFAULT_ROWHEIGHT);
		//ROW4
		tableInitialCapacityField = tableInfoPortlet.addField(new FormPortletTextField("InitialCapacity"));
		tableInitialCapacityField.setLeftPosPx(LEFTPOS).setTopPosPx(TOPPOS + 120).setWidthPx(150).setHeightPx(DEFAULT_ROWHEIGHT);
		
		//disable editing table options except for name when in edit mode
		if(!this.isAdd) {
			disableAllTableOptionsEditingExceptForName();
		}

		tableInfoPortlet.addFormPortletListener(this);
		//init table
		this.columnMetadata = new FastTablePortlet(generateConfig(), new BasicTable(
				new Class<?>[] { String.class, String.class, String.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class,
						Boolean.class, String.class, Integer.class },
				new String[] { "columnName", "dataType", "options", "noNull", "nobroadcast", "enum", "compact", "ascii", "bitmap", "ondisk", "cache", "cacheValue", "position" }),
				"Column Configuration");
		AmiWebFormatterManager fm = service.getFormatterManager();
		this.columnMetadata.getTable().addColumn(true, "Column Name", "columnName", fm.getBasicFormatter()).setWidth(150);
		this.columnMetadata.getTable().addColumn(true, "Data Type", "dataType", fm.getBasicFormatter());

		this.columnMetadata.getTable().addColumn(true, "Options", "options", fm.getBasicFormatter());
		this.columnMetadata.getTable().hideColumn("options");

		this.columnMetadata.getTable().addColumn(true, "NN", "noNull", fm.getCheckboxWebCellFormatter()).setWidth(30).setJsFormatterType("checkbox");
		this.columnMetadata.getTable().addColumn(true, "NB", "nobroadcast", fm.getCheckboxWebCellFormatter()).setWidth(30).setJsFormatterType("checkbox");
		this.columnMetadata.getTable().addColumn(true, "EM", "enum", fm.getCheckboxWebCellFormatter()).setWidth(30).setJsFormatterType("checkbox");
		this.columnMetadata.getTable().addColumn(true, "CM", "compact", fm.getCheckboxWebCellFormatter()).setWidth(30).setJsFormatterType("checkbox");
		this.columnMetadata.getTable().addColumn(true, "AS", "ascii", fm.getCheckboxWebCellFormatter()).setWidth(30).setJsFormatterType("checkbox");
		this.columnMetadata.getTable().addColumn(true, "BM", "bitmap", fm.getCheckboxWebCellFormatter()).setWidth(30).setJsFormatterType("checkbox");
		this.columnMetadata.getTable().addColumn(true, "OD", "ondisk", fm.getCheckboxWebCellFormatter()).setWidth(30).setJsFormatterType("checkbox");
		this.columnMetadata.getTable().addColumn(true, "CA", "cache", fm.getCheckboxWebCellFormatter()).setWidth(30).setJsFormatterType("checkbox");
		this.columnMetadata.getTable().addColumn(true, "Cache Value", "cacheValue", fm.getBasicFormatter()).setWidth(100);

		this.columnMetadata.getTable().addColumn(true, "Position", "position", fm.getIntegerWebCellFormatter());
		this.columnMetadata.getTable().hideColumn("position");
		editableColumnIds.put("columnName", new TableEditableColumn("columnName", WebColumnEditConfig.EDIT_TEXTFIELD));
		editableColumnIds.put("dataType",
				new TableEditableColumn("dataType",
						Arrays.asList(new String[] { AmiConsts.TYPE_NAME_STRING, AmiConsts.TYPE_NAME_ENUM, AmiConsts.TYPE_NAME_LONG, AmiConsts.TYPE_NAME_INTEGER,
								AmiConsts.TYPE_NAME_BYTE, AmiConsts.TYPE_NAME_SHORT, AmiConsts.TYPE_NAME_DOUBLE, AmiConsts.TYPE_NAME_FLOAT, AmiConsts.TYPE_NAME_BOOLEAN,
								AmiConsts.TYPE_NAME_UTC, AmiConsts.TYPE_NAME_UTCN, AmiConsts.TYPE_NAME_BINARY, AmiConsts.TYPE_NAME_ENUM, AmiConsts.TYPE_NAME_CHAR,
								AmiConsts.TYPE_NAME_BIGINT, AmiConsts.TYPE_NAME_BIGDEC, AmiConsts.TYPE_NAME_COMPLEX, AmiConsts.TYPE_NAME_UUID })));
		//editableColumnIds.put("options", new TableEditableColumn("options", WebColumnEditConfig.EDIT_TEXTFIELD));
		editableColumnIds.put("noNull", new TableEditableColumn("noNull", WebColumnEditConfig.EDIT_CHECKBOX));
		editableColumnIds.put("nobroadcast", new TableEditableColumn("nobroadcast", WebColumnEditConfig.EDIT_CHECKBOX));
		editableColumnIds.put("enum", new TableEditableColumn("enum", WebColumnEditConfig.EDIT_CHECKBOX));
		editableColumnIds.put("compact", new TableEditableColumn("compact", WebColumnEditConfig.EDIT_CHECKBOX));
		editableColumnIds.put("ascii", new TableEditableColumn("ascii", WebColumnEditConfig.EDIT_CHECKBOX));
		editableColumnIds.put("bitmap", new TableEditableColumn("bitmap", WebColumnEditConfig.EDIT_CHECKBOX));
		editableColumnIds.put("ondisk", new TableEditableColumn("ondisk", WebColumnEditConfig.EDIT_CHECKBOX));
		editableColumnIds.put("cache", new TableEditableColumn("cache", WebColumnEditConfig.EDIT_CHECKBOX));
		editableColumnIds.put("cacheValue", new TableEditableColumn("cacheValue", WebColumnEditConfig.EDIT_TEXTFIELD));

		this.columnMetadata.getTable().sortRows("position", true, false, false);
		this.columnMetadata.setDialogStyle(AmiWebUtils.getService(getManager()).getUserDialogStyleManager());
		this.columnMetadata.addOption(FastTablePortlet.OPTION_TITLE_BAR_COLOR, "#6f6f6f");
		this.columnMetadata.addOption(FastTablePortlet.OPTION_TITLE_DIVIDER_HIDDEN, true);
		//add listener
		this.columnMetadata.getTable().addMenuListener(this);
		this.columnMetadata.getTable().setMenuFactory(this);

		this.userLogTable = new FastTablePortlet(generateConfig(),
				new BasicTable(new Class<?>[] { String.class, String.class, String.class, String.class, String.class, String.class, String.class }, new String[] { "type", "oldColumn", "targetColumn", "ocr", "sql","cumulative_sql",  "description" }), "User Changes");

		MapWebCellFormatter typeFormatter = new MapWebCellFormatter(new BasicTextFormatter());
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_ADD_COLUMN, "Add", "_cna=column_editor_icon_add", "&nbsp;&nbsp;&nbsp;&nbsp;Add");
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_DROP_COLUMN, "Delete", "_cna=column_editor_icon_delete", "&nbsp;&nbsp;&nbsp;&nbsp;Delete");
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_RENAME_COLUMN, "Rename Column", "_cna=column_editor_icon_update", "&nbsp;&nbsp;&nbsp;&nbsp;Rename Column");
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_MODIFY_COLUMN, "Modify Column", "_cna=column_editor_icon_update", "&nbsp;&nbsp;&nbsp;&nbsp;Modify Column");
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_MOVE_COLUMN, "Move Column", "_cna=column_editor_icon_position_move", "&nbsp;&nbsp;&nbsp;&nbsp;Move Column");
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_RENAME_TABLE, "Rename Table", "_cna=column_editor_icon_update", "&nbsp;&nbsp;&nbsp;&nbsp;Rename Table");
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_WARNING, "Warning", "_cna=portlet_icon_warning", "&nbsp;&nbsp;&nbsp;&nbsp;Warning");

		this.userLogTable.getTable().addColumn(true, "Type", "type", typeFormatter).setWidth(110);
		this.userLogTable.getTable().addColumn(true, "Old Column", "oldColumn", fm.getBasicFormatter()).setWidth(130);
		this.userLogTable.getTable().addColumn(true, "Target Column", "targetColumn", fm.getBasicFormatter()).setWidth(130);
		this.userLogTable.getTable().addColumn(true, "Original Column Reference", "ocr", fm.getBasicFormatter()).setWidth(100);
		this.userLogTable.getTable().addColumn(true, "SQL", "sql", fm.getBasicFormatter()).setWidth(100);
		this.userLogTable.getTable().addColumn(true, "Cumulative SQL", "cumulative_sql", fm.getBasicFormatter()).setWidth(100);
		this.userLogTable.getTable().addColumn(true, "Description", "description", fm.getBasicFormatter()).setWidth(550);
		this.userLogTable.getTable().hideColumn("ocr");
		this.userLogTable.getTable().hideColumn("oldColumn");
		this.userLogTable.getTable().addMenuListener(this);
		this.userLogTable.getTable().setMenuFactory(this);
		DividerPortlet div1 = new DividerPortlet(generateConfig(), false, this.userLogTable, this.columnMetadata);

		this.columnMetaDataEditForm = new AmiCenterManagerColumnMetaDataEditForm(generateConfig(), null, AmiCenterManagerColumnMetaDataEditForm.MODE_EDIT);
		this.columnMetaDataEditForm.resetForm();
		GridPortlet formGrid = new GridPortlet(generateConfig());
		formGrid.addChild(this.tableInfoPortlet, 0, 0, 1, 1);
		formGrid.addChild(this.columnMetaDataEditForm, 0, 1, 1, 2);
		DividerPortlet div = new DividerPortlet(generateConfig(), true, div1, formGrid);

		this.addChild(div, 0, 0, 1, 1);
		this.addChild(buttonsFp, 0, 1);
		setRowSize(1, 40);
		div.setOffsetFromTopPx(500);
		tableInfoPortlet.addFormPortletListener(this);
	}
	
	private void disableAllTableOptionsEditingExceptForName() {
		tablePersistEngineField.setDisabled(true);
		tableBroadCastField.setDisabled(true);
		tableRefreshPeriodMsField.setDisabled(true);
		tableOnUndefColumnField.setDisabled(true);
		tableInitialCapacityField.setDisabled(true);
	}
	
	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		if (button == this.cancelButton) {
			//close the entire rich table editor
			this.getParent().getParent().close();
			return;
		}
		super.onButtonPressed(portlet, button);
	}
	
	private void insertEmptyReservedColumnRow(char c) {
		String reservedName = SH.toString(c);
		if(existingColNames.contains(reservedName)) {
			String warning = "Reserved Column " + "`" + reservedName + "`" + " already exists";
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, reservedName, null, null, null, warning);
			return;
		}
		String type = null;
		switch(c) {
			case 'A':
			case 'P':
				type = AmiConsts.TYPE_NAME_STRING;
				break;
			case 'T':
				type = AmiConsts.TYPE_NAME_ENUM;
				break;
			case 'C':
			case 'D':	
			case 'E':
			case 'M':
			case 'W':
				type = AmiConsts.TYPE_NAME_LONG;
				break;
			//don't think you could add an 'I':
			//case 'I':
				
			case 'V':
				type = AmiConsts.TYPE_NAME_INTEGER;
				break;
			default:
				throw new NullPointerException("Unknown reserved column: " + c);
		}
		if(type == null)
			throw new NullPointerException("Unknown reserved column: " + c);
		
		String sql = "ADD " + c + " " + type;
		Row r = columnMetadata.addRow(reservedName, type, null, false, false, false, false, false, false, false, false, null, -1);
		existingColNames.add(reservedName);
		colNames2rows_Table.put(reservedName, r);
		onRowInserted(r, sql);
		curColumns.add(reservedName);
		System.out.println(curColumns);
	}
	
	private void insertEmptyReservedColumnRowAt(char c, int i) {
		String reservedName = SH.toString(c);
		if(existingColNames.contains(reservedName)) {
			String warning = "Reserved Column " + "`" + reservedName + "`" + " already exists";
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, reservedName, null, null, null, warning);
			return;
		}
		String type = null;
		switch(c) {
			case 'A':
			case 'P':
				type = AmiConsts.TYPE_NAME_STRING;
				break;
			case 'T':
				type = AmiConsts.TYPE_NAME_ENUM;
				break;
			case 'C':
			case 'D':	
			case 'E':
			case 'M':
			case 'W':
				type = AmiConsts.TYPE_NAME_LONG;
				break;
			//don't think you could add an 'I':
			//case 'I':
				
			case 'V':
				type = AmiConsts.TYPE_NAME_INTEGER;
				break;
			default:
				throw new NullPointerException("Unknown reserved column: " + c);
		}
		if(type == null)
			throw new NullPointerException("Unknown reserved column: " + c);
		
		String sql = "ADD " + c + " " + type;
		if(i == curColumns.size())
			sql = sql + " AFTER " + curColumns.get(i - 1);
		else
			sql = sql + " BEFORE " + curColumns.get(i);
		Row r = columnMetadata.addRowAt(i, reservedName, type, null, false, false, false, false, false, false, false, false, null, -1);
		existingColNames.add(reservedName);
		colNames2rows_Table.put(reservedName, r);
		onRowInserted(r, sql);
		curColumns.add(i, reservedName);
		System.out.println(curColumns);
	}

	private void insertEmptyRow() {
		String nextColName = getNextColumnName("new_column");
		String dfltDataType = "String";
		String sql = "ADD " + nextColName + " " + dfltDataType;
		Row r = columnMetadata.addRow(nextColName, dfltDataType, null, false, false, false, false, false, false, false, false, null, -1);
		existingColNames.add(nextColName);
		colNames2rows_Table.put(nextColName, r);
		onRowInserted(r, sql);
		curColumns.add(nextColName);
		System.out.println(curColumns);
	}

	private void insertEmptyRowAt(int i) {
		String nextColName = getNextColumnName("new_column");
		String dfltDataType = "String";
		String sql = "ADD " + nextColName + " " + dfltDataType;
		if(i == curColumns.size())
			sql = sql + " AFTER " + curColumns.get(i - 1);
		else
			sql = sql + " BEFORE " + curColumns.get(i);
		Row r = columnMetadata.addRowAt(i, nextColName, dfltDataType, null, false, false, false, false, false, false, false, false, null, -1);
		existingColNames.add(nextColName);
		colNames2rows_Table.put(nextColName, r);
		onRowInserted(r, sql);
		curColumns.add(i, nextColName);
		System.out.println(curColumns);
	}
	
	private String getPrevCumulativeSql() {
		List<Row> existingRows = userLogTable.getTable().getRows();
		String prevCumulativeSql = null;
		//reverse loop over the row
		for(int i = existingRows.size() - 1; i >= 0; i--) {
			Row prevRow = existingRows.get(i);
			String cumulativeSql = (String) prevRow.get("cumulative_sql");
			if(cumulativeSql != null) {
				prevCumulativeSql = cumulativeSql;
				break;
			}else if(cumulativeSql == null)
				continue;
		}
		if(prevCumulativeSql == null)
			throw new IllegalStateException("should not have gotten here");
		return prevCumulativeSql;
	}
	
	private void onRowInserted(Row r, String sql) {
		String colName =(String) r.get("columnName");
		int pos = r.getLocation();
		String cumulativeSql = isFirstColumnEditRow(AmiUserEditMessage.ACTION_TYPE_ADD_COLUMN) ? sql : collapseSql(getPrevCumulativeSql(), sql);
		Row logRow = userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_ADD_COLUMN, null, colName, colName, sql, cumulativeSql, "A new column `" + colName + '`' + " is added");
		colNames2rows_Log.put(colName, logRow);
		LinkedList<String> newChain = new LinkedList<String>();
		newChain.add(colName);
		editChains.add(newChain);
	}
	
	private void onRowDeleted(Row r) {
		String colName =(String) r.get("columnName");
		String originalColRef = findHeadFromChain(colName);
		String sql = "DROP " + colName;
		int toDeleteColumnIndex = curColumns.indexOf(colName);
		if(toDeleteColumnIndex == -1)
			throw new IllegalStateException("The column to delete does not exist: " + colName);
		curColumns.remove(colName);
		System.out.println(curColumns);
		
		String cumulativeSql = isFirstColumnEditRow(AmiUserEditMessage.ACTION_TYPE_ADD_COLUMN) ? sql : collapseSql(getPrevCumulativeSql(), sql);
		userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_DROP_COLUMN, colName, colName, originalColRef, sql, cumulativeSql, "The column`" + colName + '`' + " is removed from the table");
		if(r != colNames2rows_Table.get(colName)) // this means this is a duplicate row, don't update colnames and colNames2rows_Table
			return;
		existingColNames.remove(colName);
		colNames2rows_Table.remove(colName);
		
		//delete the chain
		LinkedList<String> toDeleteChain = null;
		for(LinkedList<String> chain: editChains) {
			if(chain.getLast().equals(colName)) {
				toDeleteChain = chain;
				break;
			}
		}
		if(toDeleteChain == null)
			throw new NullPointerException("Couldn't find the chain to delete");
		editChains.remove(toDeleteChain);
	}
	
	//need to update the "Add" message in the log table for empty row
	private void onEmptyRowUpdated(String nuwColumnName, String oldColumnName, String nuwType, String oldType) {
		if(!this.isAdd)
			throw new UnsupportedOperationException("onEmptyRowUpdated() not supported in edit mode");
		String originalColumnRef = null;
		//only one node in the chain for newly added 
		for(LinkedList<String> chain : editChains) {
			if(chain.getLast().equals(oldColumnName)) {
				chain.removeLast();
				chain.add(nuwColumnName);
				originalColumnRef = chain.getLast();
				break;
			}
		}
		int toReplaceColumnIndex = curColumns.indexOf(oldColumnName);
		if(toReplaceColumnIndex == -1)
			throw new IllegalStateException("The old column does not exist: " + oldColumnName);
		curColumns.remove(oldColumnName);
		curColumns.add(toReplaceColumnIndex, nuwColumnName);
		System.out.println(curColumns);
		
		Row logRow = colNames2rows_Log.get(oldColumnName);
		String oldSql = (String) logRow.get("sql");
		String nuwSql = "ADD " + AmiUtils.escapeVarName(nuwColumnName) + " " + nuwType;
		logRow.put("description", "A new column `" + nuwColumnName + " " + nuwType + '`' + " is added");
		logRow.put("sql", nuwSql);
		logRow.put("targetColumn", nuwColumnName);
		logRow.put("ocr", originalColumnRef);
		//also need to update the cumulative sql for the remaining of the rows
		for(Row r: userLogTable.getTable().getRows()) {
			Byte type = (Byte) r.get("type");
			if(AmiUserEditMessage.ACTION_TYPE_WARNING == type)
				continue;
			String oldCumulativeSql = (String) r.get("cumulative_sql"); 
			String nuwCumulativeSql = oldCumulativeSql.replace(oldSql, nuwSql);
			r.put("cumulative_sql", nuwCumulativeSql);
		}
		colNames2rows_Log.remove(oldColumnName);
		colNames2rows_Log.put(nuwColumnName, logRow);
		//also update the table
		Row tableRow = colNames2rows_Table.get(oldColumnName);
		colNames2rows_Table.remove(oldColumnName);
		colNames2rows_Table.put(nuwColumnName, tableRow);
	}
	//need to update the "Add" message in the log table for empty row
	private void onEmptyRowDeleted(Row r) {
		String colName = (String)r.get("columnName");
		Row logRow = colNames2rows_Log.get(colName);
		userLogTable.removeRow(logRow);
		colNames2rows_Log.remove(colName);
		//also update the table	
		colNames2rows_Table.remove(colName);
				
	}
	
	
	private void onRowUpdated(Row old, Row nuw) {
		String oldColName = (String) old.get("columnName");
		String nuwColName = (String) nuw.get("columnName");
		String oldDataType = (String) old.get("dataType");
		String nuwDataType = (String) nuw.get("dataType");
		String oldOption = getOptionStringForRow(old);
		String nuwOption = getOptionStringForRow(nuw);
		String nuwType = nuwDataType + " " + nuwOption;
		String oldType = oldDataType + " " + oldOption;
		//first check if the update happens on original rows or newly added rows
		Row logRowAdd = colNames2rows_Log.get(oldColName);
		if(logRowAdd != null && this.isAdd) { //for add mode only
			onEmptyRowUpdated(nuwColName, oldColName, nuwType, oldType);
		} else {
			System.out.println("the update is happening on orig rows");
			 //if it is only the column name that has changed
			if(!SH.equals(oldColName, nuwColName) && SH.equals(oldDataType, nuwDataType) && isSameColumnOptions(old, nuw))
				onRowUpdated_Rename(oldColName, nuwColName);
			else if(!SH.equalsIgnoreCase(oldDataType, nuwDataType) || !oldOption.equalsIgnoreCase(nuwOption))
				onRowUpdated_Modify(oldColName, nuwColName, nuwType);
		}
	}
	
	//before adding the row to the columnMeta table check if this is the first column edit row. The first column edit row has "sql == cumulativeSql"
	private boolean isColumnEditRowExist() {
		List<Row> existingRows = userLogTable.getTable().getRows();
		if(existingRows.isEmpty())
			return false;
		for(int i = 0; i< userLogTable.getTable().getRows().size(); i++) {
			Row prevRow = userLogTable.getTable().getRows().get(i);
			byte prevType = (byte) prevRow.get("type");
			if(prevType != AmiUserEditMessage.ACTION_TYPE_WARNING && prevType != AmiUserEditMessage.ACTION_TYPE_RENAME_TABLE ) {
				return true;
			}
		}
		return false;
	}
	
	private boolean isFirstColumnEditRow(byte actionType) {
		if(actionType == AmiUserEditMessage.ACTION_TYPE_WARNING || actionType == AmiUserEditMessage.ACTION_TYPE_RENAME_TABLE)
			return false;
		return !isColumnEditRowExist();
		
	}
	
	private void onColumnRenamed(String nuw, String old) {
		String originalColumnRef = null;
		//use linkedlist instead
		boolean chainTailfound = false;
		for(LinkedList<String> chain : editChains) {
			if(chain.getLast().equals(old)) {
				chain.add(nuw);
				originalColumnRef = chain.getFirst();
				chainTailfound = true;
				break;
			}
		}
		if(!chainTailfound)
			throw new IllegalStateException("Chain tail not found");
		
		int toReplaceColumnIndex = curColumns.indexOf(old);
		if(toReplaceColumnIndex == -1)
			throw new IllegalStateException("The old column does not exist: " + old);
		curColumns.remove(old);
		curColumns.add(toReplaceColumnIndex, nuw);
		System.out.println(curColumns);
		
		//userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_RENAME_COLUMN, old, colName, originalColumnRef, "The column `" + old + '`' + " has been renamed to " + '`' + nuw + '`');
		Row columnRow = colNames2rows_Table.get(old);
		columnRow.put("columnName", nuw);
		colNames2rows_Table.remove(old);
		colNames2rows_Table.put(nuw, columnRow);
		
	}
	//corresponds to "ALTER TABLE BLA MODFIY COL AS NEW_COL TYPE";
	private void onRowUpdated_Modify(String origColName, String nuwColName, String nuwType) {
		String sql =  "MODIFY " + origColName + " AS " + nuwColName + " " + nuwType;
		if(!SH.equals(origColName, nuwColName))
			onColumnRenamed(nuwColName, origColName);
		String originalColumnRef = findHeadFromChain(nuwColName);
		String cumulativeSql = isFirstColumnEditRow(AmiUserEditMessage.ACTION_TYPE_ADD_COLUMN) ? sql : collapseSql(getPrevCumulativeSql(), sql);
		userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_MODIFY_COLUMN, origColName, nuwColName, originalColumnRef, sql, cumulativeSql, "The column `" + origColName + '`' + " has been modified to " + '`' + nuwColName + " " + nuwType + '`');

	}
	
	//corresponds to "ALTER TABLE BLA RENAME COL TO NEW_NAME";
	private void onRowUpdated_Rename(String old, String nuw) {
		String sql = "RENAME " + AmiUtils.escapeVarName(old) + " TO " + AmiUtils.escapeVarName(nuw);
		onColumnRenamed(nuw, old);
		String originalColumnRef = findHeadFromChain(nuw);
		String cumulativeSql = isFirstColumnEditRow(AmiUserEditMessage.ACTION_TYPE_ADD_COLUMN) ? sql : collapseSql(getPrevCumulativeSql(), sql);
		userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_RENAME_COLUMN, old, nuw, originalColumnRef, sql, cumulativeSql, "The column `" + old + '`' + " has been renamed to " + '`' + nuw + '`');

	}
	
	//corresponds to "ALTER TABLE BLA MOVE COL BEFORE/AFTER ...";
	private void onRowUpdated_Move(String colName, int old, int nuw) {
		String sql = "MOVE " + AmiUtils.escapeVarName(colName);
		if(nuw > old)
			sql += " AFTER " + AmiUtils.escapeVarName(getColumnFromPosition(nuw));
		else
			sql += " BEFORE " + AmiUtils.escapeVarName(getColumnFromPosition(nuw));
		
	    // Remove the column from old position
	    String col = curColumns.remove(old);
	    // Insert it at the new position
	    curColumns.add(nuw, col);
	    System.out.println(curColumns);
		
		String originalColumnRef = findHeadFromChain(colName);
		String cumulativeSql = isFirstColumnEditRow(AmiUserEditMessage.ACTION_TYPE_ADD_COLUMN) ? sql : collapseSql(getPrevCumulativeSql(), sql);
		userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_MOVE_COLUMN, colName, colName, originalColumnRef, sql, cumulativeSql, "The position for column `" + colName + '`' + " has been changed from " + '`' + old + '`' + " to " + '`' + nuw + '`');
	}
	
	public String getColumnFromPosition(int pos) {
		return curColumns.get(pos);
	}
	
	
	private static boolean isSameColumnOptions(Row old, Row nuw) {
		String oldOption = getOptionStringForRow(old);
		String nuwOption = getOptionStringForRow(nuw);
		Map oldMap = parseOptions(oldOption);
		Map nuwMap = parseOptions(nuwOption);
		boolean isSame = oldMap.equals(nuwMap);
		return isSame;
	}
	
	
	//find the original column reference from the current column name
	private String findHeadFromChain(String curColName) {
		String head = null;
		for(LinkedList<String> chain: editChains) {
			if(chain.getLast().equals(curColName)) {
				head = chain.getFirst();
				break;
			}	
		}
		if(head == null)
			throw new IllegalStateException("Chain head not found");
		return head;
	}

	public AmiCenterManagerEditColumnPortlet(PortletConfig config, String tableSql, AmiCenterGraphNode_Table correlationNode) {
		this(config, false);
		this.correlationNode = correlationNode;
		this.sql = tableSql;
		this.importFromText(tableSql, new StringBuilder());
		enableEdit(false);
	}

	public SmartTable getColumnTable() {
		return this.columnMetadata.getTable().getTable();
	}

	private static Map<String, String> parseOptions(String option) {
		HasherMap<String, String> m = new HasherMap<String, String>(CaseInsensitiveHasher.INSTANCE);
		List<String> l = SH.splitToList(" ", option);
		for (String s : l) {
			String key, value = null;
			if (s.contains("=")) {
				key = SH.beforeFirst(s, "=");
				value = SH.afterFirst(s, "=");
			} else {
				key = s;
				value = "true";
			}
			m.put(key, value);
		}
		return m;
	}

	private void prepareRequestToBackend(String query) {
		AmiCenterQueryDsRequest request = prepareRequest();
		request.setQuery(query);
		service.sendRequestToBackend(this, request);
	}

	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		List<Table> tables = response.getTables();
		if (response.getOk() && tables != null && tables.size() == 1) {
			Table t = tables.get(0);
			for (Row r : t.getRows()) {
				String columnName = (String) r.get("ColumnName");
				String dataType = AmiUtils.toTypeName(AmiUtils.parseTypeName((String) r.get("DataType")));
				String options = SH.noNull((String) r.get("Options"));//null is considered empty string
				Map<String, String> storageOptions = parseOptions(options);
				Boolean noBroadcast = storageOptions.containsKey("NoBroadcast");
				Boolean enm = storageOptions.containsKey("Enum");
				Boolean compact = storageOptions.containsKey("Compact");
				Boolean ascii = storageOptions.containsKey("Ascii");
				Boolean bitmap = storageOptions.containsKey("BITMAP");
				Boolean ondisk = storageOptions.containsKey("OnDisk");
				Boolean cache = storageOptions.containsKey("Cache");
				String cacheVal = storageOptions.get("Cache") == null ? null : SH.replaceAll(storageOptions.get("Cache"), '"', "");
				Boolean noNull = (Boolean) r.get("NoNull");
				Integer position = (Integer) r.get("Position");
				Row toAdd = columnMetadata.addRow(columnName, dataType, options, noNull, noBroadcast, enm, compact, ascii, bitmap, ondisk, cache, cacheVal, position);
				colNames2rows_Table.put(columnName, toAdd);
				existingColNames.add(columnName);
				Map<String, String> colMap = CH.m(KEY_COLUMN_DATATYPE, dataType, KEY_COLUMN_POS, SH.toString(r.getLocation()), KEY_COLUMN_OPTIONS, options);
				origColumnConfig.put(columnName, colMap);
				origColNames.add(columnName);
				//init the edit chain
				LinkedList<String> singletonChain = new LinkedList<String>();
				singletonChain.add(columnName);
				editChains.add(singletonChain);
				curColumns.add(columnName);
			}
			
		}

	}

	public void initColumnMetadata(String t) {
		this.columnMetadata.clearRows();
		prepareRequestToBackend("SHOW COLUMNS WHERE TableName ==" + "\"" + t + "\" ORDER BY Position;");
	}

	private void onUserEditStart() {
		if (!this.columnMetadata.isEditing())
			this.columnMetadata.startEdit(columnMetadata.getTable().getSelectedRows(), this.editableColumnIds, this);
	}

	@Override
	public void onUserDblclick(FastWebColumns columns, String action, Map<String, String> properties) {
		//edit logic goes here
		if (this.enableColumnEditing || this.isAdd) {
			int selectedCount = this.columnMetadata.getTable().getSelectedRows().size();
			if (selectedCount == 1)
				onUserEditStart();
		}
	}
	
	@Override
	public void onContextMenu(WebTable table, String action) {
		if (SH.startsWith(action, "add_column_")) {
			String temp = SH.afterFirst(action, "add_column_");
			int loc = table.getSelectedRows().get(0).getLocation();
			if (temp.startsWith("before")) {
				insertEmptyRowAt(loc);
			} else if (temp.startsWith("after")) {
				insertEmptyRowAt(loc + 1);
			}
		} else if (SH.startsWith(action, "add_rc_")) {
			char c = SH.toUpperCase(action.charAt(7));
			if(action.toCharArray().length == 8)
				insertEmptyReservedColumnRow(c);
			else if("before".equals(action.substring(9, 15))) {
				int loc = table.getSelectedRows().get(0).getLocation();
				insertEmptyReservedColumnRowAt(c, loc);
			} else if("after".equals(action.substring(9, 14))) {
				int loc = table.getSelectedRows().get(0).getLocation();
				insertEmptyReservedColumnRowAt(c, loc + 1);
			}			
		}  else if ("drop_column".equals(action)) {
			for (Row r : table.getSelectedRows()) {
				columnMetadata.removeRow(r);
				if(isAdd)// || !origColumnConfig.containsKey((String)r.get("columnName")))
					onEmptyRowDeleted(r);
				else
					onRowDeleted(r);
			}
				
			return;
		} else if ("add_column".equals(action)) {
			insertEmptyRow();
		} else if ("move_up".equals(action) || "move_down".equals(action) || ("move_to".equals(action))) {
			Row toMove = table.getSelectedRows().get(0);
			String colName = (String) toMove.get("columnName");
			int origSize = columnMetadata.getTable().getRowsCount();
			int origLoc = toMove.getLocation();
			Integer moveToIndex = null;
			if ("move_up".equals(action)) {
				columnMetadata.removeRow(toMove);
				moveToIndex = origLoc == 0 ? origSize - 1 : origLoc - 1;
				Row nuwRow = columnMetadata.addRowAt(moveToIndex, toMove);
				onRowUpdated_Move(colName, origLoc, moveToIndex);
			} else if ("move_down".equals(action)) {
				columnMetadata.removeRow(toMove);
				moveToIndex = origLoc == origSize - 1 ? 0 : origLoc + 1;
				columnMetadata.addRowAt(moveToIndex, toMove);
				onRowUpdated_Move(colName, origLoc, moveToIndex);
			} else {
				InputsPortlet p = new InputsPortlet(generateConfig(), table.getSelectedRows().get(0), origSize - 1, origLoc);
				RootPortlet root = (RootPortlet) this.service.getPortletManager().getRoot();
				int width = MH.min(500, (int) (root.getWidth() * 0.4));
				int height = MH.min(100, (int) (root.getHeight() * 0.4));
				getManager().showDialog("Move Column To...", p, width, height);
			}		
		} else if("clear_all_warnings".equals(action)) {
			for(Row r: userLogTable.getTable().getRows()) {
				Byte actionType = (Byte) r.get("type");
				String col = (String) r.get("targetColumn");
				if(AmiUserEditMessage.ACTION_TYPE_WARNING == actionType) {
					userLogTable.removeRow(r);
					colNames2rows_Log.remove(col);
				}
					
			}
		} else if("clear_selected_warnings".equals(action)) {
			List<Row> selected = table.getSelectedRows();
			for(Row r: selected) {
				Byte actionType = (Byte) r.get("type");
				String col = (String) r.get("targetColumn");
				if(AmiUserEditMessage.ACTION_TYPE_WARNING == actionType) {
					userLogTable.removeRow(r);
					colNames2rows_Log.remove(col);
				}
			}
		} else if("navigate_to".equals(action)) {
			Row logRow = table.getSelectedRows().get(0);
			String colName = (String) logRow.get("targetColumn");
			Row targetColumnRow = colNames2rows_Table.get(colName);
			if(targetColumnRow == null)
				return;
			columnMetadata.getTable().setSelectedRows(new int[] {targetColumnRow.getLocation()});
		}

	}
	
	@Override
	public void onCellClicked(WebTable table, Row row, WebColumn col) {
	}

	@Override
	public void onCellMousedown(WebTable table, Row row, WebColumn col) {
	}

	public static Tuple2<Integer, String> parseCacheValue(String s) {
		Tuple2<Integer, String> cacheValue = new Tuple2<Integer, String>();
		StringBuilder digitBuilder = new StringBuilder();
		StringBuilder unitBuilder = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (c == '"')
				continue;
			if (Character.isDigit(c))
				digitBuilder.append(c);
			else
				unitBuilder.append(c);
		}
		cacheValue.setA(Integer.parseInt(digitBuilder.toString()));
		cacheValue.setB(unitBuilder.toString());
		return cacheValue;
	}

	public class InputsPortlet extends GridPortlet implements FormPortletListener {

		private Row row;
		int origLoc;
		private FormPortlet form;
		private FormPortletNumericRangeField indexField;
		private FormPortletButton cancelButton;
		private FormPortletButton submitButton;

		public InputsPortlet(PortletConfig config, Row row, int maxIndex, int origLoc) {
			super(config);
			this.row = row;
			this.origLoc = origLoc;
			this.form = new FormPortlet(generateConfig());
			this.addChild(form);
			this.indexField = this.form.addField(new FormPortletNumericRangeField("Move To Index: ", 0, maxIndex, 0));
			this.form.addFormPortletListener(this);
			this.submitButton = this.form.addButton(new FormPortletButton("Submit"));
			this.cancelButton = this.form.addButton(new FormPortletButton("Cancel"));
		}

		@Override
		public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
			if (this.cancelButton == button)
				close();
			else {
				int nuwPos = indexField.getValue().intValue();
				if (nuwPos == origLoc) {
					AmiCenterManagerUtils.popDialog(service, "The Row is already at position:" + nuwPos, "Error Moving Column");
					return;
				}
				columnMetadata.removeRow(row);
				columnMetadata.addRowAt(nuwPos, row);
				onRowUpdated_Move((String)row.get("columnName"), origLoc, nuwPos);
			}
			close();
		}

		@Override
		public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		}

		@Override
		public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		}
	}

	private void setNoBroadCast(Map<String, String> m) {
		FormPortletCheckboxField noBroadcastEditField = (FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName("NoBroadcast");
		Boolean noBroadcast = Caster_Boolean.INSTANCE.cast(m.get(AmiConsts.NOBROADCAST));
		noBroadcastEditField.setValue(noBroadcast);
		noBroadcastEditField.setDisabled(false);
	}

	private FormPortletCheckboxField getColumnOptionEditField(String name) {
		return (FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName(name);
	}

	@Override
	public void onSelectedChanged(FastWebTable fastWebTable) {
		if(fastWebTable == userLogTable.getTable())
			return;
		Row activeRow = fastWebTable.getActiveRow();
		if (activeRow != null)
			onRowSelected(activeRow);

	}

	@Override
	public void onNoSelectedChanged(FastWebTable fastWebTable) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onScroll(int viewTop, int viewPortHeight, long contentWidth, long contentHeight) {
		// TODO Auto-generated method stub

	}
	
	private static BasicWebMenu addReservedColumnsMenu(String title, byte addPosition, String col) {
		BasicWebMenu reservedColsMenus = new BasicWebMenu(title, true);
		String postfix = null;
		switch(addPosition) {
			case ADD_RESERVED_COLUMN_DFLT:
				postfix = "";
				break;
			case ADD_RESERVED_COLUMN_BEFORE:
				postfix = "_before_" + col;
				break;
			case ADD_RESERVED_COLUMN_AFTER:
				postfix = "_after_" + col;
				break;
			
		}
		reservedColsMenus.add(new BasicWebMenuLink("A (AMI-Center)", true, "add_rc_a" + postfix));
		reservedColsMenus.add(new BasicWebMenuLink("C (Created Time)", true, "add_rc_c" + postfix));
		reservedColsMenus.add(new BasicWebMenuLink("D (AMI-ID)", true, "add_rc_d" + postfix));
		reservedColsMenus.add(new BasicWebMenuLink("E (Expires Time)", true, "add_rc_e" + postfix));
		//don't think you can add an I
		//reservedColsMenus.add(new BasicWebMenuLink("I (UniqueID)", true, "add_rc_i" + postfix));
		reservedColsMenus.add(new BasicWebMenuLink("M (Modified Time)", true, "add_rc_m" + postfix));
		reservedColsMenus.add(new BasicWebMenuLink("P (Application)", true, "add_rc_p" + postfix));
		reservedColsMenus.add(new BasicWebMenuLink("V (Revision)", true, "add_rc_v" + postfix));
		reservedColsMenus.add(new BasicWebMenuLink("T (Table Name)", true, "add_rc_t" + postfix));
		reservedColsMenus.add(new BasicWebMenuLink("W (Current Time)", true, "add_rc_w" + postfix));
		return reservedColsMenus;
	}

	@Override
	public WebMenu createMenu(WebTable table) {
		FastWebTable ftw = (FastWebTable) table;
		BasicWebMenu m = new BasicWebMenu();
		int selectedRowSize = ftw.getSelectedRows().size();
		if(ftw == columnMetadata.getTable()) {
			m.add(new BasicWebMenuLink("Add Column", true, "add_column"));
			BasicWebMenu reservedColsMenus = addReservedColumnsMenu("Add Reserved Column", ADD_RESERVED_COLUMN_DFLT, null);
			m.add(reservedColsMenus);
			if (ftw.getActiveRow() != null) {
				m.add(new BasicWebMenuLink("Drop Column", true, "drop_column"));
				switch (selectedRowSize) {
					case 1:
						int origRowPos = ftw.getActiveRow().getLocation();
						String origColumnName = (String) ftw.getActiveRow().get("columnName");
						m.add(new BasicWebMenuLink("Move Up", true, "move_up"));
						m.add(new BasicWebMenuLink("Move Down", true, "move_down"));
						m.add(new BasicWebMenuLink("Move To Index...", true, "move_to"));
						m.add(new BasicWebMenuLink("Add Column Before " + origColumnName, true, "add_column_before_" + origColumnName));
						m.add(new BasicWebMenuLink("Add Column After " + origColumnName, true, "add_column_after_" + origColumnName));
						m.add(addReservedColumnsMenu("Add Reserved Column Before", ADD_RESERVED_COLUMN_BEFORE, origColumnName));
						m.add(addReservedColumnsMenu("Add Reserved Column After", ADD_RESERVED_COLUMN_AFTER, origColumnName ));
						break;
					default:
						break;
				}
			}
		}else if(ftw == userLogTable.getTable()) {
			m.add(new BasicWebMenuLink("Clear All Warning(s)", true, "clear_all_warnings"));
			Row activeRow = ftw.getActiveRow();
			if (activeRow != null) {
				Byte actionType = (Byte) activeRow.get("type");
				if(AmiUserEditMessage.ACTION_TYPE_WARNING == actionType)
					m.add(new BasicWebMenuLink("Clear Selected Warnings", true, "clear_selected_warnings"));
				if(selectedRowSize == 1)
					m.add(new BasicWebMenuLink("Navigate To", true, "navigate_to"));

			}
		}
		
		return m;
	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
	}

	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		return null;
	}

	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField node) {
	}

	public void onRowSelected(Row row) {
		String dataType = (String) row.get("dataType");
		String columnName = (String) row.get("columnName");
		Boolean noNull = (Boolean) row.get("noNull");
		Boolean nobroadcast = (Boolean) row.get("nobroadcast");
		Boolean enm = (Boolean) row.get("enum");
		Boolean compact = (Boolean) row.get("compact");
		Boolean ascii = (Boolean) row.get("ascii");
		Boolean bitmap = (Boolean) row.get("bitmap");
		Boolean ondisk = (Boolean) row.get("ondisk");
		Boolean cache = (Boolean) row.get("cache");
		String cacheVal = row.get("cacheValue") == null ? null : SH.replaceAll((String) row.get("cacheValue"), '"', "");
		Integer position = (Integer) row.get("position");
		FormPortletTextField f1 = (FormPortletTextField) this.columnMetaDataEditForm.getForm().getFieldByName("columnName");
		f1.setValue(columnName);

		FormPortletSelectField<Byte> f2 = (FormPortletSelectField) this.columnMetaDataEditForm.getForm().getFieldByName("dataType");
		f2.setValue(AmiUtils.parseTypeName(dataType));

		((FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName("noNull")).setValue(noNull);
		((FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName("nobroadcast")).setValue(nobroadcast);
		((FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName("enum")).setValue(enm);
		((FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName("compact")).setValue(compact);
		((FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName("ascii")).setValue(ascii);
		((FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName("bitmap")).setValue(bitmap);
		((FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName("ondisk")).setValue(ondisk);
		((FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName("bitmap")).setValue(bitmap);
		((FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName("cache")).setValue(cache);
		((FormPortletTextField) this.columnMetaDataEditForm.getForm().getFieldByName("cacheValue")).setValue(cacheVal);
	}

	@Override
	public String prepareUseClause() {
		StringBuilder sb = new StringBuilder();
		String persisengine = tablePersistEngineField.getValue();
		if (!AmiCenterEntityConsts.PERSIST_ENGINE_TYPE_NONE.equals(persisengine))
			sb.append("PersistEngine = ").append(SH.doubleQuote(persisengine)).append(' ');
		if (!tableBroadCastField.getBooleanValue())
			sb.append("NoBroadcast");
		if (SH.is(tableRefreshPeriodMsField.getValue()))
			sb.append(" RefreshPeriodMs = ").append(SH.doubleQuote(tableRefreshPeriodMsField.getValue()));
		sb.append(" OnUndefColumn = ").append(SH.doubleQuote(tableOnUndefColumnField.getValue()));
		if (SH.is(tableInitialCapacityField.getValue()))
			sb.append(" InitialCapacity = ").append(SH.doubleQuote(tableInitialCapacityField.getValue())).append(';');
		return sb.toString();
	}

	@Override
	public String preparePreUseClause() {
		StringBuilder sb = new StringBuilder();
		String tableName = tableNameField.getValue();
		sb.append("CREATE PUBLIC TABLE ");
		AmiUtils.escapeVarName(tableName, sb);
		sb.append('(');
		//schema
		Table t = this.columnMetadata.getTable().getTable();
		Iterator<Row> iter = t.getRows().iterator();
		while (iter.hasNext()) {
			Row row = iter.next();
			String dataType = (String) row.get("dataType");
			String columnName = (String) row.get("columnName");
			AmiUtils.escapeVarName(columnName, sb);
			sb.append(" ").append(dataType);

			Boolean noNull = (Boolean) row.get("noNull");
			if (noNull)
				sb.append(' ').append("NoNull");
			Boolean nobroadcast = (Boolean) row.get("nobroadcast");
			if (nobroadcast)
				sb.append(' ').append("NoBroadcast");
			//TODO:what do we do with enum?
			Boolean enm = (Boolean) row.get("enum");
			Boolean compact = (Boolean) row.get("compact");
			if (compact)
				sb.append(' ').append("Compact");
			Boolean ascii = (Boolean) row.get("ascii");
			if (ascii)
				sb.append(' ').append("Ascii");
			Boolean bitmap = (Boolean) row.get("bitmap");
			if (bitmap)
				sb.append(' ').append("BITMAP");
			Boolean ondisk = (Boolean) row.get("ondisk");
			if (ondisk)
				sb.append(' ').append("OnDisk");
			Boolean cache = (Boolean) row.get("cache");
			if (cache)
				sb.append(' ').append("Cache ");
			String cacheVal = (String) row.get("cacheValue");
			if (SH.is(cacheVal))
				sb.append('=').append(SH.doubleQuote(cacheVal));
			if (iter.hasNext())
				sb.append(',');
		}
		sb.append(')');
		return sb.toString();
	}

	@Override
	public String exportToText() {
		return previewScript();
	}

	@Override
	public void importFromText(String text, StringBuilder sink) {
		try {
			CreateTableNode cn = AmiCenterManagerUtils.scriptToCreateTableNode(text);
			Map<String, String> tableConfig = AmiCenterManagerUtils.parseAdminNode_Table(cn);
			this.origConfig = tableConfig;
			String tableName = tableConfig.get("name");
			tableNameField.setDefaultValue(tableName);
			tableNameField.setValue(tableName);
			initColumnMetadata(tableName);
			for (Entry<String, String> e : tableConfig.entrySet()) {
				String key = e.getKey();
				String value = e.getValue();
				if ("PersistEngine".equals(key)) {
					String valueToSet = value == null ? AmiCenterEntityConsts.PERSIST_ENGINE_TYPE_NONE : value;
					tablePersistEngineField.setValue(valueToSet);
					tablePersistEngineField.setDefaultValue(valueToSet);
				} else if ("BroadCast".equals(key)) {
					Boolean boolVal = Caster_Boolean.INSTANCE.cast(value);
					boolVal = boolVal == null ? true : boolVal;//dflt is true
					tableBroadCastField.setValue(boolVal);
					tableBroadCastField.setDefaultValue(boolVal);
				} else if ("RefreshPeriodMs".equals(key)) {
					tableRefreshPeriodMsField.setValue(value);
					tableRefreshPeriodMsField.setDefaultValue(value);
				} else if ("OnUndefColumn".equals(key)) {
					if(value == null)
						value = AmiCenterEntityConsts.ON_UNDEF_COLUMN_OPTION_IGNORE;
					tableOnUndefColumnField.setValue(value);
					tableOnUndefColumnField.setDefaultValue(value);
				} else if ("InitialCapacity".equals(key)) {
					tableInitialCapacityField.setValue(value);
					tableInitialCapacityField.setDefaultValue(value);
				}
			}
		} catch (ExpressionParserException e) {
			AmiCenterManagerUtils.popDialog(service, e.toLegibleString(), "Error Importing Script");
		}

		//TODO: parse schema and populate table

	}

	public void enableColumnEditing(boolean enable) {
		this.enableColumnEditing = enable;
	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		super.onFieldValueChanged(portlet, field, attributes);
		onFieldChanged(field);		
		if(field == this.tableNameField) {
			if(!OH.eq(tableNameField.getValue(), tableNameField.getDefaultValue())) {
				if(renameTableLogRow == null) {
					String sql = "RENAME TABLE " + tableNameField.getDefaultValue() + " TO " + tableNameField.getValue();
					Row r = userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_RENAME_TABLE, null, "<TABLE>", null, sql, null, "The table has been renamed from `" + tableNameField.getDefaultValue() + "`" + " to `" + tableNameField.getValue() + "`");
					this.renameTableLogRow = r;
				}else
					updateRenameRow();
			}else if(OH.eq(tableNameField.getValue(), tableNameField.getDefaultValue()) && renameTableLogRow != null)
				removeRenameRow();
		}
	}
	
	//there can at most exist one update row(or none)
	private void updateRenameRow() {
		OH.assertTrue(renameTableLogRow != null);
		String sql = "RENAME TABLE " + tableNameField.getDefaultValue() + " TO " + tableNameField.getValue();
		userLogTable.removeRow(renameTableLogRow);
		Row r = userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_RENAME_TABLE, null, "<TABLE>", null, sql, null, "The table has been renamed from `" + tableNameField.getDefaultValue() + "`" + " to `" + tableNameField.getValue() + "`");
		this.renameTableLogRow = r;
	}
	
	private void removeRenameRow() {
		OH.assertTrue(renameTableLogRow != null);
		userLogTable.removeRow(renameTableLogRow);
		renameTableLogRow = null;
		
	}

	@Override
	public void enableEdit(boolean enable) {
		for (FormPortletField<?> fpf : this.tableInfoPortlet.getFormFields()) {
			if (fpf != this.enableEditingCheckbox)
				fpf.setDisabled(!enable);
		}
		if(!this.isAdd)
			disableAllTableOptionsEditingExceptForName();
		enableColumnEditing(enable);

	}
	
	private void revertEditOnColumn(Row oldRow, String warning) {
		//find the row being edited
		String colName = (String)oldRow.get("columnName");
		int rowEditedIndex = curColumns.indexOf(colName);
		if(rowEditedIndex == -1)
			throw new NullPointerException("column not found: " + colName);
		Row rowEdited = this.columnMetadata.getTable().getRow(rowEditedIndex);
		for(Entry<String, Object> e: oldRow.entrySet()) {
			String key = e.getKey();
			String val = (String) e.getValue();
			if("true".equals(val) || "false".equals(val))
				rowEdited.put(key, Caster_Boolean.INSTANCE.cast(val));
			else
				rowEdited.put(key, val);
		}	
		AmiCenterManagerUtils.popDialog(service, warning + ".\r\n Reverting editing", "Error Editing Column");
	}


	@Override
	public void onTableEditComplete(Table origTable, Table editedTable, FastTablePortlet fastTablePortlet, StringBuilder errorSink) {
		if (errorSink.length() > 0) {
			this.columnMetadata.finishEdit();
			getManager().showAlert(errorSink.toString());
			return;
		}
		if (editedTable.getSize() == 0) {
			this.columnMetadata.finishEdit();
			return;
		}
		this.columnMetadata.finishEdit();
		if (editedTable.getRows().size() != 1)
			throw new UnsupportedOperationException("Only one row is allowed to be edited at a time");
		Row r = editedTable.getRow(0);
		Row origRow = origTable.getRow(0);
		
		//check mutual exclusive column options
		Boolean isCompact = Caster_Boolean.INSTANCE.cast(r.get("compact"));
		Boolean isAscii = Caster_Boolean.INSTANCE.cast(r.get("ascii"));
		Boolean isBitmap = Caster_Boolean.INSTANCE.cast(r.get("bitmap"));
		Boolean isOndisk = Caster_Boolean.INSTANCE.cast(r.get("ondisk"));
		Boolean isEnum = Caster_Boolean.INSTANCE.cast(r.get("enum"));
		Boolean isCache = Caster_Boolean.INSTANCE.cast(r.get("cache"));
		String cacheValue = (String) r.get("cacheValue");
		String type = (String) r.get("dataType");
		String nuwColName = (String) r.get("columnName");
		String oldColName = (String) origRow.get("columnName");
		
		//RULE: Reserved Columns Check
		//reserved columns cannot be modified to anything else
		if(oldColName.length() == 1 && RESERVED_COLUMN_NAMES.contains(oldColName.charAt(0))) {
			String warning = "Can not modify reserved column: " + oldColName.charAt(0);
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, oldColName, oldColName, null, null, warning);
			revertEditOnColumn(origRow, warning);
			return;
		}
		//normal columns cannot be renamed to reserved columns
		if(nuwColName.length() == 1 && RESERVED_COLUMN_NAMES.contains(nuwColName.charAt(0))) {
			String warning = "Can not rename column " + oldColName + " to reserved column name " + nuwColName.charAt(0);
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, oldColName, oldColName, null, null, warning);
			revertEditOnColumn(origRow, warning);
			return;
		}
		
		//RULE0: Check new value validity(For example, empty string is not allowed)
		if(SH.isnt(nuwColName)) {
			String warning = "Invalid Column Name: " + "`"+ nuwColName + "`";
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, (String) r.get("columnName"), oldColName, null, null, warning);
			revertEditOnColumn(origRow, warning);
			return;
		}
			
		//RULE0: No duplicate column name allowed
		if(!SH.equals(nuwColName, oldColName) && existingColNames.contains(nuwColName)) {
			String warning = "Duplicate column Name: " + nuwColName;
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, (String) r.get("columnName"), oldColName, null, null, warning);
			revertEditOnColumn(origRow, warning);
			return;
		}
		
		//RULE0: COMPACT/BITMAP directive only supported for STRING columns
		if((isAscii || isBitmap || isCompact) && !"String".equalsIgnoreCase(type)) {
			String warning = "ASCII/COMPACT/BITMAP directive only supported for STRING columns";
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, (String) r.get("columnName"), null, null, null, warning);
			revertEditOnColumn(origRow, warning);
			return;
		}

		//RULE1: ONDISK can not be used in conjunction with other supplied directives,aka (isOndisk && (isAscii || isBitmap || isEnum)) should be disallowed
		if(isOndisk && (isAscii || isBitmap || isEnum)) {
			String warning = "ONDISK can not be used in conjunction with other supplied directives";
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, (String) r.get("columnName"), null, null, null, warning);
			revertEditOnColumn(origRow, warning);
			return;
		}
		
		//RULE2: BITMAP and COMPACT directive are mutually exclusive, aka disallow (isCompact && isBitmap)
		if(isCompact && isBitmap) {
			String warning = "BITMAP and COMPACT directive are mutually exclusive";
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, (String) r.get("columnName"), null, null, null, warning);
			revertEditOnColumn(origRow, warning);
			return;
		}


		//RULE3: ASCII directive only supported for STRING columns with COMPACT option, aka disallow (isAscii && !isCompact)
		if(isAscii && !isCompact) {
			String warning = "ASCII directive only supported for STRING columns with COMPACT option";
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, (String) r.get("columnName"), null, null, null, warning);
			revertEditOnColumn(origRow,warning);
			return;
		}

		//RULE4: ONDISK directive only supported for STRING and BINARY columns
		if(isOndisk && !"String".equalsIgnoreCase(type) && !"Binary".equalsIgnoreCase(type)) {
			String warning = "ONDISK directive only supported for STRING and BINARY columns";
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, (String) r.get("columnName"), null, null, null, warning);
			revertEditOnColumn(origRow,warning);
			return;
		}
		
		//RULE5: cache value cannot be empty
		if(isCache && !isValidCacheValue(cacheValue)) {
			String warning = "Cache value cannot be empty. Supported units are: KB, MB, GB and TB (if no unit is specified, then bytes)";
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, (String) r.get("columnName"), null, null, null, warning);
			revertEditOnColumn(origRow, warning);
			return;
		}
		
		//RULE6: need to switch on cache before configuring cache value
		if(SH.is(cacheValue) && !isCache) {
			String warning = "Need to enable cache before configuring cache value";
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING, null, (String) r.get("columnName"), null, null, null, warning);
			revertEditOnColumn(origRow, warning);
			return;
		}
		
		
		
		
		//update the log table if the column name has changed for added empty row
		if(isAdd) { //|| !origColumnConfig.containsKey((String)origRow.get("columnName"))
			String colNameNuw = (String) r.get("columnName");
			String colNameOld = (String) origRow.get("columnName");
			String oldDataType = (String) origRow.get("dataType");
			String nuwDataType = (String) r.get("dataType");
			String oldOption = getOptionStringForRow(origRow);
			String nuwOption = getOptionStringForRow(r);
			String nuwType = nuwDataType + " " + nuwOption;
			String oldType = oldDataType + " " + oldOption;
			if(!colNameNuw.equals(colNameOld))
				onEmptyRowUpdated(colNameNuw, colNameOld, nuwType, oldType);
		}else {
			onRowUpdated(origRow, r);
		}
		
		//sync to edit form on the right
		for (Entry<String, Object> e : r.entrySet()) {
			String key = e.getKey();
			Object val = e.getValue();
			if ("true".equals(val) || "false".equals(val)) {
				boolean boolVal = Caster_Boolean.INSTANCE.cast(val);
				FormPortletCheckboxField f = (FormPortletCheckboxField) this.columnMetaDataEditForm.getForm().getFieldByName(key);
				f.setValue(boolVal);
			} else if ("columnName".equals(key)) {
				FormPortletTextField colNameField = (FormPortletTextField) this.columnMetaDataEditForm.getForm().getFieldByName("columnName");
				String curVal = (String) val;
				String origColName = (String) origRow.get("columnName");
				colNameField.setValue(curVal);
				if (!SH.equals(origColName, curVal)) {
					existingColNames.remove(origColName);
					existingColNames.add(curVal);
				}

			} else if ("dataType".equals(key)) {
				FormPortletSelectField<Byte> dataTypeField = (FormPortletSelectField) this.columnMetaDataEditForm.getForm()
						.getFieldByName(AmiCenterManagerColumnMetaDataEditForm.VARNAME_COLUMN_DATA_TYPE);
				dataTypeField.setValue(AmiUtils.parseTypeName((String) val));
			} else if ("cacheValue".equals(key)) {
				FormPortletTextField f = (FormPortletTextField) this.columnMetaDataEditForm.getForm().getFieldByName(key);
				f.setValue((String) val);
			}
		}
		
	}
	
	private static boolean isValidCacheValue(String cacheValue) {
		if(cacheValue == null)
			return false;
		cacheValue = SH.trim(cacheValue);
		Pattern CACHE_VALUE_PATTERN = Pattern.compile("^\\d+(KB|MB|GB|TB)?$");
		return CACHE_VALUE_PATTERN.matcher(cacheValue).matches();
	}
	
	
	@Override
	public void onTableEditAbort(FastTablePortlet fastTablePortlet) {
		return;
	}

	@Override
	public void onEditCell(int x, int y, String v) {
		final WebColumn pos = this.columnMetadata.getTable().getVisibleColumn(x);
		final String[] cols = pos.getTableColumns();
		final Column col = this.columnMetadata.getTable().getTable().getColumn(cols[0]);
		WebCellFormatter f = pos.getCellFormatter();
		if (f instanceof WebCellStyleWrapperFormatter)
			f = ((WebCellStyleWrapperFormatter) f).getInner();
		final Object v2;
		if (f instanceof NumberWebCellFormatter) {
			NumberWebCellFormatter f2 = (NumberWebCellFormatter) f;
			try {
				v2 = SH.isEmpty(v) ? null : f2.getFormatter().parse(v);
			} catch (Exception e) {
				return;
			}
		} else
			v2 = v;
		final Object cast = col.getTypeCaster().cast(v2, false, false);

		//Check mutual exclusiveness
		final Column dataTypeCol = this.columnMetadata.getTable().getTable().getColumn("dataType");
		final Column bitmapCol = this.columnMetadata.getTable().getTable().getColumn("bitmap");
		final Column enumCol = this.columnMetadata.getTable().getTable().getColumn("enum");
		final Column compactCol = this.columnMetadata.getTable().getTable().getColumn("compact");
		final Column asciiCol = this.columnMetadata.getTable().getTable().getColumn("ascii");
		final Column ondiskCol = this.columnMetadata.getTable().getTable().getColumn("ondisk");
		final Column cacheValCol = this.columnMetadata.getTable().getTable().getColumn("cacheValue");			
		//Cache must used in conjunction with ondisk
		if ("cache".equals(col.getId()) && "true".equals(v)) {
			ondiskCol.setValue(y, true);
		}
		
		if (y < this.columnMetadata.getTable().getRowsCount())
			this.columnMetadata.getTable().getRow(y).putAt(col.getLocation(), cast);
	}

	@Override
	public Object getEditOptions(WebColumnEditConfig cfg, Row row) {
		//convert List<String> to comma-delimetered strings
		List<String> listOptions = cfg.getEditSelectOptions();
		String strOptions = SH.join(',', listOptions);
		return strOptions;
	}

	public static class TableEditableColumn implements WebColumnEditConfig {
		private String columnId;
		private String editOptionFormula;
		private List<String> editSelectOptions;
		private byte editType;//checkbox, textfield, select

		public TableEditableColumn(String columnId, byte editType) {
			this.columnId = columnId;
			this.editType = editType;
		}

		public TableEditableColumn(String columnId, List<String> editSelectOptions) {
			this(columnId, WebColumnEditConfig.EDIT_SELECT);
			this.editSelectOptions = editSelectOptions;
		}

		@Override
		public String getEditId() {
			return getColumnId();
		}

		@Override
		public String getColumnId() {
			return columnId;
		}

		@Override
		public String getEditOptionFormula() {
			return editOptionFormula;
		}

		@Override
		public List<String> getEditSelectOptions() {
			return editSelectOptions;
		}

		@Override
		public byte getEditType() {
			return editType;
		}

		@Override
		public int getEnableLastNDays() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean getDisableFutureDays() {
			throw new UnsupportedOperationException();

		}

	}

	public String getNextColumnName(String name) {
		String columnName = SH.getNextId(name, existingColNames);
		return columnName;
	}
	

	
	public final static Comparator<Row> COMPARATOR_DELETE_COLUMN_FIRST = new Comparator<Row>() {

		@Override
		public int compare(Row o1, Row o2) {
			Byte type1 = (Byte)o1.get("type");
			Byte type2 = (Byte)o2.get("type");
			int score1 = 0;
			int score2 = 0;
			if(type1 == AmiUserEditMessage.ACTION_TYPE_DROP_COLUMN)
				score1 = 2;
			else if(type1 == AmiUserEditMessage.ACTION_TYPE_RENAME_TABLE)
				score1 = 1;
			
			if(type2 == AmiUserEditMessage.ACTION_TYPE_DROP_COLUMN)
				score2 = 2;
			else if(type2 == AmiUserEditMessage.ACTION_TYPE_RENAME_TABLE)
				score1 = 1;
			return -OH.compare(score1, score2);
		}

	};
	
	private void previewSql_UnOptimized(StringBuilder sb) {
		Iterable<Row> rows = userLogTable.getTable().getRows();
		for(Row r: rows) {
			String sql = (String) r.get("sql");
			Byte type = (Byte)r.get("type");
			switch(type) {
				case AmiUserEditMessage.ACTION_TYPE_ADD_COLUMN:
				case AmiUserEditMessage.ACTION_TYPE_DROP_COLUMN:
				case AmiUserEditMessage.ACTION_TYPE_RENAME_COLUMN:
				case AmiUserEditMessage.ACTION_TYPE_MOVE_COLUMN:
				case AmiUserEditMessage.ACTION_TYPE_MODIFY_COLUMN:
					sb.append(sql).append(", ");
					break;
				case AmiUserEditMessage.ACTION_TYPE_RENAME_TABLE:
				case AmiUserEditMessage.ACTION_TYPE_WARNING:
			}	
		}
		if (sb.length() > 0) {
		    sb.setLength(sb.length() - 2);
		    sb.append(';');
		}
	}

	@Override
	public String previewEdit() {
		StringBuilder sb = new StringBuilder();
		if(renameTableLogRow != null) {
			String sql = "RENAME TABLE " + AmiUtils.escapeVarName(tableNameField.getDefaultValue()) + " TO " + AmiUtils.escapeVarName(tableNameField.getValue());
			sb.append(sql).append(';').append(SH.NEWLINE);
		}
		sb.append("ALTER PUBLIC TABLE ").append(AmiUtils.escapeVarName(tableNameField.getDefaultValue())).append(' ');
		previewSql_UnOptimized(sb);
		
		return sb.toString();
	}

	@Override
	protected void revertEdit() {
		super.revertEdit();
		//also revert the table
		userLogTable.clearRows();
		colNames2rows_Table.clear();
		existingColNames.clear();
		origColumnConfig.clear();
		origColNames.clear();
		editChains.clear();
		curColumns.clear();

		importFromText(sql, new StringBuilder());
		
		

	}
	
	//prevSql = "rename a to a1"; curSql = "rename a1 to a2", then resultant sql should be "rename a to a2"
	//A more complex example: prevSql = "rename b to b1, rename c to d, move d before a"; curSql = "modify d as d_ string"
	//To collapse them, we need to loop over each single sql in prevsql and see if each sql can collapse with cursql
	public String collapseSql(String prevSql, String curSql) {
		StringBuilder  resultantSqlBuilder = new StringBuilder();
		//break down prevSql
		List<String> prevSqls = SH.splitToList(",", prevSql);
		int collapseFailedAttempts = 0;
		for(String prevSingleton: prevSqls) {
			String resultSql = collapseSingletonSql(prevSingleton, curSql);
			if(SH.equals(resultSql, prevSingleton))
				collapseFailedAttempts++; 
			if(resultantSqlBuilder.length() == 0)
				resultantSqlBuilder.append(resultSql);
			else if(SH.is(resultSql))
				resultantSqlBuilder.append(',').append(resultSql);
			else if(SH.isnt(resultSql))
				resultantSqlBuilder.append("");
		}
		boolean allCollapseFails = collapseFailedAttempts == prevSqls.size();
		//if the curSql cannot collapse with any of the previous sql, just add cursql to the cumulativesql
		if(allCollapseFails) {
			if(resultantSqlBuilder.length() == 0)
				resultantSqlBuilder.append(curSql);
			else
				resultantSqlBuilder.append(',').append(curSql);
		}
			
		return resultantSqlBuilder.toString();
	}
	
	public static final Pattern RENAME_PATTERN = Pattern.compile("^RENAME\\s+(\\S+)\\s+TO\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);
	public static final Pattern MODIFY_PATTERN = Pattern.compile(
            "^MODIFY\\s+(\\S+)\\s+AS\\s+(\\S+)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        );
	
	public static final Pattern ADD_PATTERN = Pattern.compile("^ADD\\s+(\\w+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);  //Pattern.compile("^ADD\\s+(\\w+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
	public static final Pattern MOVE_PATTERN = Pattern.compile("^MOVE\\s+(\\w+)\\s+(BEFORE|AFTER)\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
	
	public String collapseSingletonSql(String prevSingleton, String curSql) {
		String resultSql = null;
		String keyword_prev = SH.beforeFirst(prevSingleton, ' ');
		String keyword_cur = SH.beforeFirst(curSql, ' ');
		boolean canCollapse = false;
		//permutate over [RENAME, MODIFY, DROP, ADD, MOVE] 5*5 = 25 scenarios
		if(SH.equals("RENAME", keyword_prev)) {
			Matcher prevMatcher = RENAME_PATTERN.matcher(prevSingleton);
			if(prevMatcher.matches()) {
				String prev_rename_from = prevMatcher.group(1);
				String prev_rename_to = prevMatcher.group(2);
				if(SH.equals("RENAME", keyword_cur)) {	
					Matcher curMatcher = RENAME_PATTERN.matcher(curSql);
					if(curMatcher.matches()) {
						String cur_rename_from = curMatcher.group(1);
						String cur_rename_to = curMatcher.group(2);
						
						if(SH.equals(prev_rename_to, cur_rename_from)) {
							canCollapse = true;
							resultSql = "RENAME " + AmiUtils.escapeVarName(prev_rename_from) + " TO " + AmiUtils.escapeVarName(cur_rename_to);
						}
					}else
						throw new IllegalStateException("Invalid RENAME CLAUSE, no match found");		
				}else if(SH.equals("MODIFY", keyword_cur)) {
					Matcher curMatcher = MODIFY_PATTERN.matcher(curSql);
					if(curMatcher.matches()) {
						String cur_oldName  = curMatcher.group(1);
			            String cur_newName  = curMatcher.group(2);
			            String cur_type = curMatcher.group(3);
			            
			            if(SH.equals(prev_rename_to, cur_oldName)) {
			            	canCollapse = true;
			            	resultSql = "MODIFY " + AmiUtils.escapeVarName(prev_rename_from) + " AS " + AmiUtils.escapeVarName(cur_newName) + " " + cur_type;
			            }
					}else
						throw new IllegalStateException("Invalid MODIFY CLAUSE, no match found");		
					
				} else if(SH.equals("DROP", keyword_cur)) {
					String cur_col2Drop = SH.afterFirst(curSql, " ");
					
					 if(SH.equals(prev_rename_to, cur_col2Drop)) {
						 canCollapse = true;
						 resultSql = "DROP " + AmiUtils.escapeVarName(prev_rename_from);
					 }				
				} else if(SH.equals("ADD", keyword_cur) || SH.equals("MOVE", keyword_cur)) {
					//no op
					canCollapse = false;
				}
			}else
				throw new IllegalStateException("Invalid RENAME CLAUSE, no match found");
		} else if(SH.equals("MODIFY", keyword_prev)){
			Matcher prevMatcher = MODIFY_PATTERN.matcher(prevSingleton);
			if(prevMatcher.matches()) {
				String prev_oldName  = prevMatcher.group(1);
	            String prev_newName  = prevMatcher.group(2);
	            String prev_type = prevMatcher.group(3);
				if(SH.equals("RENAME", keyword_cur)) {	   
		            Matcher curMatcher = RENAME_PATTERN.matcher(curSql);
		            if(curMatcher.matches()) {
		            	String cur_oldName = curMatcher.group(1);
			            String cur_newName = curMatcher.group(2);
			            
			            if(SH.equals(prev_newName, cur_oldName)) {
			            	canCollapse = true;
							resultSql = "MODIFY " + AmiUtils.escapeVarName(prev_oldName) + " AS " + AmiUtils.escapeVarName(cur_newName) + " " + prev_type;
			            }
		            }else
		            	throw new IllegalStateException("Invalid RENAME CLAUSE, no match found");
				}else if(SH.equals("MODIFY", keyword_cur)) {
					Matcher curMatcher = MODIFY_PATTERN.matcher(curSql);
					if(curMatcher.matches()) {
						String cur_oldName  = curMatcher.group(1);
			            String cur_newName  = curMatcher.group(2);
			            String cur_type = curMatcher.group(3);
			            
			            if(SH.equals(prev_newName, cur_oldName)) {
			            	canCollapse = true;
							resultSql = "MODIFY " + AmiUtils.escapeVarName(prev_oldName) + " AS " + AmiUtils.escapeVarName(cur_newName) + " " + cur_type;
			            }
					}else
						throw new IllegalStateException("Invalid MODIFY CLAUSE, no match found");
					
				}else if(SH.equals("DROP", keyword_cur)) {
					String cur_col2Drop = SH.afterFirst(curSql, " ");
					
					 if(SH.equals(prev_newName, cur_col2Drop)) {
						 canCollapse = true;
						 resultSql = "DROP " + AmiUtils.escapeVarName(prev_oldName);
					 }		
				}else if(SH.equals("ADD", keyword_cur) || SH.equals("MOVE", keyword_cur)) {
					//no op
					canCollapse = false;
				}
			}else
				throw new IllegalStateException("Invalid MODIFY CLAUSE, no match found");
			
		} else if(SH.equals("DROP", keyword_prev)) {
			//no op
			canCollapse = false;
		} else if(SH.equals("ADD", keyword_prev)) {
			Matcher preMatcher = ADD_PATTERN.matcher(prevSingleton);
			if(preMatcher.matches()) {
				String prev_add_col_name = preMatcher.group(1);
				if(SH.equals("RENAME", keyword_cur)) {	   
		            Matcher curMatcher = RENAME_PATTERN.matcher(curSql);
		            if(curMatcher.matches()) {
		            	String cur_oldName = curMatcher.group(1);
			            String cur_newName = curMatcher.group(2);
			            
			            if(SH.equals(prev_add_col_name, cur_oldName) && !curColumns.contains(cur_newName)) {
			            	String replaced = preMatcher.replaceFirst("ADD " + AmiUtils.escapeVarName(cur_newName) + " $2");
			            	canCollapse = true;
							resultSql = replaced;
			            }
		            }else
		            	throw new IllegalStateException("Invalid ADD CLAUSE, no match found");
		            
				}else if(SH.equals("MODIFY", keyword_cur)) {
					Matcher curMatcher = MODIFY_PATTERN.matcher(curSql);
					if(curMatcher.matches()) {
						String cur_oldName  = curMatcher.group(1);
			            String cur_newName  = curMatcher.group(2);
			            String cur_type = curMatcher.group(3);
			            
			            if(SH.equals(prev_add_col_name, cur_oldName)) {
			            	String replaced = preMatcher.replaceFirst("$1" + AmiUtils.escapeVarName(cur_newName) + "$3");
			            	canCollapse = true;
							resultSql = replaced;
			            }
					}else
						throw new IllegalStateException("Invalid MODIFY CLAUSE, no match found");
					
				}else if(SH.equals("DROP", keyword_cur)) {
					String cur_col2Drop = SH.afterFirst(curSql, " ");
					
					 if(SH.equals(prev_add_col_name, cur_col2Drop)) {
						 canCollapse = true;
						 resultSql = "";
					 }				
					
				}else if(SH.equals("ADD", keyword_cur) || SH.equals("MOVE", keyword_cur)) {
					//no op
					canCollapse = false;
				}
			}else
				throw new IllegalStateException("Invalid ADD CLAUSE, no match found");
			
		}else if(SH.equals("MOVE", keyword_prev)) {
			Matcher preMatcher = MOVE_PATTERN.matcher(prevSingleton);
			if(preMatcher.matches()){
				String prev_move = preMatcher.group(1);
				if(SH.equals("DROP", keyword_cur)) {
					String cur_col2Drop = SH.afterFirst(curSql, " ");
					
					 if(SH.equals(prev_move, cur_col2Drop)) {
						 canCollapse = true;
						 resultSql = "";
					 }				
					
				} else if(SH.equals("MOVE", keyword_cur)) {
					Matcher postMatcher = MOVE_PATTERN.matcher(curSql);
					if(postMatcher.matches()) {
						String post_move = postMatcher.group(1);
						if(SH.equals(prev_move, post_move)) {
							canCollapse = true;
							//TODO:also need to check if the position has changed at all
							 resultSql = curSql;
						}else
							canCollapse = false;
					}else
						throw new IllegalStateException("Invalid MOVE CLAUSE, no match found");
					
				} else if(SH.equals("ADD", keyword_cur) ||  SH.equals("RENAME", keyword_cur) ||  SH.equals("MODIFY", keyword_cur)) {//TODO: optimize move
					//no op
					canCollapse = false;
				}
			}else
				throw new IllegalStateException("Invalid MOVE CLAUSE, no match found");
		
		}
				
		if(!canCollapse)
			resultSql = prevSingleton;
		
		
		
		return resultSql;
	}
	
	
	public static String getOptionStringForRow(Row r) {
		StringBuilder sb = new StringBuilder();
		Boolean isNb = Caster_Boolean.INSTANCE.cast(r.get("nobroadcast"));
		Boolean isNn = Caster_Boolean.INSTANCE.cast(r.get("noNull"));
		Boolean isCompact = Caster_Boolean.INSTANCE.cast(r.get("compact"));
		Boolean isAscii = Caster_Boolean.INSTANCE.cast(r.get("ascii"));
		Boolean isBitmap = Caster_Boolean.INSTANCE.cast(r.get("bitmap"));
		Boolean isOndisk = Caster_Boolean.INSTANCE.cast(r.get("ondisk"));
		Boolean isEnum = Caster_Boolean.INSTANCE.cast(r.get("enum"));
		Boolean isCache = Caster_Boolean.INSTANCE.cast(r.get("cache"));
		if(isNb)
			sb.append("Nobroadcast ");
		if(isNn)
			sb.append("Nonull ");
		if(isCompact)
			sb.append("Compact ");
		if(isAscii)
			sb.append("Ascii ");
		if(isBitmap)
			sb.append("Bitmap ");
		if(isOndisk)
			sb.append("Ondisk ");
		if(isEnum)
			sb.append("Enum ");
		if(isCache)
			sb.append("Cache ");
		if(isCache) {
			String cacheValue = Caster_String.INSTANCE.cast(r.get("cacheValue"));
			if(cacheValue != null)
				sb.append("=").append(cacheValue);
		}
		return sb.toString();
			
	}
	
	public boolean hasEditChanges() {
		int logRowCnt = userLogTable.getTable().getRowsCount();
		if(logRowCnt == 0) {
			return false;
		}
		//check the last non-warning row's cumulative sql
		String lastCumulativeSql = null;
		for(int i = logRowCnt - 1; i >= 0; i--) {
			Row r = userLogTable.getTable().getRows().get(i);
			byte type = (Byte) r.get("type");
			if(type == AmiUserEditMessage.ACTION_TYPE_WARNING)
				continue;
			String cumulativeSql = (String) r.get("cumulative_sql");
			lastCumulativeSql = cumulativeSql;
		}
		return SH.is(lastCumulativeSql);
	}

	@Override
	public boolean ensureCanProceedWithApply() {
		//first check all required fields are filled in(aka namefield)
		if(SH.isnt(this.tableNameField.getValue())) {
			AmiCenterManagerUtils.popDialog(service, "The table name field cannot be empty", "Error Applying Changes");
			return false;
		}
		
		//ensure the table should at least have one column
		if(columnMetadata.getTable().getRows().size() < 1) {
			AmiCenterManagerUtils.popDialog(service, "The table should at least have one column", "Error Applying Changes");
			return false;
		}
		
		//if no changes detected
		if(!hasEditChanges()) {
			AmiCenterManagerUtils.popDialog(service, "No Changes detected", "Error Applying Changes");
			return false;
		}
		
		return true;
	}
	
	
	public static void main(String[] args) {
//		String input = "ADD colA STRING NONULL AFTER colB";
//        String newName = "myNewCol";
//
//        Pattern pattern = Pattern.compile("^ADD\\s+(\\w+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
//        Matcher matcher = pattern.matcher(input);
//
//        if (matcher.matches()) {
//        	String replaced = matcher.replaceFirst("ADD " + newName + " $2");
//            System.out.println("Original: " + input);
//            System.out.println("Replaced: " + replaced);
//        } else {
//            System.out.println("No match!");
//        }
		
		//String input = "ADD new_column String BITMAP ASCII";
//		String input = "ADD new_column String";
//
//        Pattern pattern = Pattern.compile("^ADD\\s+(\\w+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
//        Matcher matcher = pattern.matcher(input);
//        String tt =  matcher.group(1);
//        if (matcher.matches()) {
//            String name = matcher.group(1);      // "new_column"
//            String typeAndOptions = matcher.group(2); // "String BITMAP ASCII"
//
//            System.out.println("Column name: " + name);
//            System.out.println("Type + Options: " + typeAndOptions);
//        } else {
//            System.out.println("No match!");
//        }

		 String[] inputs = {
		            "MOVE `colA A` BEFORE colB",
		            "MOVE `user_id B` AFTER id"
		        };

		        Pattern pattern = Pattern.compile("^MOVE\\s+(\\w+)\\s+(BEFORE|AFTER)\\s+(\\w+)$",
		                                          Pattern.CASE_INSENSITIVE);

		        for (String input : inputs) {
		            Matcher matcher = pattern.matcher(input);
		            if (matcher.matches()) {
		                String colToMove   = matcher.group(1);
		                String direction   = matcher.group(2);
		                String anotherCol  = matcher.group(3);

		                System.out.println("Input: " + input);
		                System.out.println("  colToMove:  " + colToMove);
		                System.out.println("  direction:  " + direction);
		                System.out.println("  anotherCol: " + anotherCol);
		                System.out.println();
		            } else {
		                System.out.println("No match for: " + input);
		            }
		        }
		
	}

}
