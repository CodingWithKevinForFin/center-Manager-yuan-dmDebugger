package com.f1.ami.web.centermanager.editors;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.f1.ami.amicommon.AmiConsts;
import com.f1.ami.amicommon.AmiUtils;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.amiscript.AmiDebugMessage;
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
import com.f1.utils.SH;
import com.f1.utils.casters.Caster_Boolean;
import com.f1.utils.casters.Caster_String;
import com.f1.utils.concurrent.HasherMap;
import com.f1.utils.formatter.BasicTextFormatter;
import com.f1.utils.impl.CaseInsensitiveHasher;
import com.f1.utils.string.ExpressionParserException;
import com.f1.utils.string.sqlnode.CreateTableNode;
import com.f1.utils.structs.Tuple2;
import com.f1.utils.structs.table.BasicTable;
import com.f1.utils.structs.table.SmartTable;

public class AmiCenterManagerEditColumnPortlet extends AmiCenterManagerAbstractEditCenterObjectPortlet
		implements WebContextMenuListener, WebContextMenuFactory, FastTableEditListener {
	private static final String BG_GREY = "_bg=#4c4c4c";
	private static final int LEFTPOS = 120;
	private static final int TOPPOS = 20;
	
	public static final byte UPDATE_TYPE_COLUMN_NAME = 1;
	public static final byte UPDATE_TYPE_COLUMN_TYPE = 2;
	public static final byte UPDATE_TYPE_COLUMN_OPTIONS = 4;
	public static final byte UPDATE_TYPE_COLUMN_POSITION = 8;
	
	public static final String KEY_COLUMN_NAME = "name";
	public static final String KEY_COLUMN_DATATYPE = "type";
	public static final String KEY_COLUMN_OPTIONS = "options";
	public static final String KEY_COLUMN_POS = "position";
	
	

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
	private HashMap<String, Row> colNames2rows_Log = new HashMap<String, Row>();
	private Set<String> existingColNames = new HashSet<String>();
	final private AmiCenterManagerColumnMetaDataEditForm columnMetaDataEditForm;
	private String sql;

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
				new BasicTable(new Class<?>[] { String.class, String.class, String.class }, new String[] { "type", "targetColumn", "description" }), "User Changes");

		MapWebCellFormatter typeFormatter = new MapWebCellFormatter(new BasicTextFormatter());
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_ADD, "Add", "_cna=column_editor_icon_add", "&nbsp;&nbsp;&nbsp;&nbsp;Add");
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_DELETE, "Delete", "_cna=column_editor_icon_delete", "&nbsp;&nbsp;&nbsp;&nbsp;Delete");
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_UPDATE, "Update", "_cna=column_editor_icon_update", "&nbsp;&nbsp;&nbsp;&nbsp;Update");
		typeFormatter.addEntry(AmiUserEditMessage.ACTION_TYPE_WARNING, "Warning", "_cna=portlet_icon_warning", "&nbsp;&nbsp;&nbsp;&nbsp;Warning");

		this.userLogTable.getTable().addColumn(true, "Type", "type", typeFormatter).setWidth(100);
		this.userLogTable.getTable().addColumn(true, "Target Column", "targetColumn", fm.getBasicFormatter()).setWidth(100);
		this.userLogTable.getTable().addColumn(true, "Description", "description", fm.getBasicFormatter()).setWidth(550);

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

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		if (button == this.cancelButton) {
			//close the entire rich table editor
			this.getParent().getParent().close();
			return;
		}
		super.onButtonPressed(portlet, button);
	}

	private void insertEmptyRow() {
		String nextColName = getNextColumnName("new_column");
		String dfltDataType = "String";
		Row r = columnMetadata.addRow(nextColName, dfltDataType, null, false, false, false, false, false, false, false, false, null, -1);
		existingColNames.add(nextColName);
		colNames2rows_Table.put(nextColName, r);
		onRowInserted(r);
	}

	private void insertEmptyRowAt(int i) {
		String nextColName = getNextColumnName("new_column");
		String dfltDataType = "String";
		Row r = columnMetadata.addRowAt(i, nextColName, dfltDataType, null, false, false, false, false, false, false, false, false, null, -1);
		existingColNames.add(nextColName);
		colNames2rows_Table.put(nextColName, r);
		onRowInserted(r);
	}
	
	private void onRowInserted(Row r) {
		String colName =(String) r.get("columnName");
		int pos = r.getLocation();
		Row logRow = userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_ADD, colName, "A new column `" + colName + '`' + " is added");
		colNames2rows_Log.put(colName, logRow);
	}
	
	private void onRowDeleted(Row r) {
		String colName =(String) r.get("columnName");
		int pos = r.getLocation();
		userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_DELETE,colName,"The column`" + colName + '`' + " is removed from the table at position " + pos);
	}
	
	//need to update the "Add" message in the log table for empty row
	private void onEmptyRowUpdated(String nuwColumnName, String oldColumnName) {
		Row logRow = colNames2rows_Log.get(oldColumnName);
		logRow.put("description", "A new column `" + nuwColumnName + '`' + " is added");
		logRow.put("targetColumn", nuwColumnName);
		colNames2rows_Log.remove(oldColumnName);
		colNames2rows_Log.put(nuwColumnName, logRow);
	}
	//need to update the "Add" message in the log table for empty row
	private void onEmptyRowDeleted(Row r) {
		String colName = (String)r.get("columnName");
		Row logRow = colNames2rows_Log.get(colName);
		userLogTable.removeRow(logRow);
		colNames2rows_Log.remove(colName);
	}
	
	private void processUpdate(byte changes,Row r, StringBuilder msg) {
		boolean nameChanged = MH.anyBits(changes, UPDATE_TYPE_COLUMN_NAME);
		boolean typeChanged = MH.anyBits(changes, UPDATE_TYPE_COLUMN_TYPE);
		boolean optionChanged = MH.anyBits(changes, UPDATE_TYPE_COLUMN_OPTIONS);
		boolean posChanged = MH.anyBits(changes, UPDATE_TYPE_COLUMN_POSITION);
		if(nameChanged)
			onRowUpdated(UPDATE_TYPE_COLUMN_NAME, r, msg);
		if(typeChanged)
			onRowUpdated(UPDATE_TYPE_COLUMN_TYPE, r, msg);
		if(optionChanged)
			onRowUpdated(UPDATE_TYPE_COLUMN_OPTIONS, r, msg);
		if(posChanged)
			onRowUpdated(UPDATE_TYPE_COLUMN_POSITION, r, msg);
		
	}
	
	private void onRowUpdated(byte type, Row nuwRow, StringBuilder msg) {
		switch(type) {
			case UPDATE_TYPE_COLUMN_NAME:
			case UPDATE_TYPE_COLUMN_TYPE:
			case UPDATE_TYPE_COLUMN_OPTIONS:
			case UPDATE_TYPE_COLUMN_POSITION:
				
		}
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
				columnMetadata.addRow(columnName, dataType, options, noNull, noBroadcast, enm, compact, ascii, bitmap, ondisk, cache, cacheVal, position);
				existingColNames.add(columnName);
			}
		}

	}

	public void initColumnMetadata(String t) {
		this.columnMetadata.clearRows();
		prepareRequestToBackend("SHOW COLUMNS WHERE TableName ==" + "\"" + t + "\";");
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

		} else if ("drop_column".equals(action)) {
			for (Row r : table.getSelectedRows()) {
				columnMetadata.removeRow(r);
				if(isAdd)
					onEmptyRowDeleted(r);
				else
					onRowDeleted(r);
			}
				
			return;
		} else if ("add_column".equals(action)) {
			insertEmptyRow();
		} else if ("move_up".equals(action) || "move_down".equals(action) || ("move_to".equals(action))) {
			Row toMove = table.getSelectedRows().get(0);
			int origSize = columnMetadata.getTable().getRowsCount();
			int origLoc = toMove.getLocation();
			Integer moveToIndex = null;
			if ("move_up".equals(action)) {
				columnMetadata.removeRow(toMove);
				moveToIndex = origLoc == 0 ? origSize - 1 : origLoc - 1;
				Row nuwRow = columnMetadata.addRowAt(moveToIndex, toMove);
				Tuple2<Map<String,Object>, Map<String, Object>> changes = new Tuple2<Map<String,Object>, Map<String,Object>>();
				Map<String,Object> old = CH.m("position",origLoc);
				Map<String,Object> nuw = CH.m("position",nuwRow.getLocation());
				changes.setA(old);
				changes.setB(nuw);
				//onRowUpdated(UPDATE_TYPE_COLUMN_POSITION, toMove.get("columnName"), changes);
			} else if ("move_down".equals(action)) {
				columnMetadata.removeRow(toMove);
				moveToIndex = origLoc == origSize - 1 ? 0 : origLoc + 1;
				columnMetadata.addRowAt(moveToIndex, toMove);
			} else {
				InputsPortlet p = new InputsPortlet(generateConfig(), table.getSelectedRows().get(0), origSize - 1, origLoc);
				RootPortlet root = (RootPortlet) this.service.getPortletManager().getRoot();
				int width = MH.min(500, (int) (root.getWidth() * 0.4));
				int height = MH.min(100, (int) (root.getHeight() * 0.4));
				getManager().showDialog("Move Column To...", p, width, height);
			}
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

	@Override
	public WebMenu createMenu(WebTable table) {
		FastWebTable ftw = (FastWebTable) table;
		BasicWebMenu m = new BasicWebMenu();
		m.add(new BasicWebMenuLink("Add Column", true, "add_column"));
		if (ftw.getActiveRow() != null) {
			m.add(new BasicWebMenuLink("Drop Column", true, "drop_column"));
			switch (ftw.getSelectedRows().size()) {
				case 1:
					int origRowPos = ftw.getActiveRow().getLocation();
					String origColumnName = (String) ftw.getActiveRow().get("columnName");
					m.add(new BasicWebMenuLink("Move Up", true, "move_up"));
					m.add(new BasicWebMenuLink("Move Down", true, "move_down"));
					m.add(new BasicWebMenuLink("Move To Index...", true, "move_to"));
					m.add(new BasicWebMenuLink("Add Column Before " + origColumnName, true, "add_column_before_" + origColumnName));
					m.add(new BasicWebMenuLink("Add Column After " + origColumnName, true, "add_column_after_" + origColumnName));
					break;
				default:
					break;
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

		//		switch (AmiUtils.parseTypeName(dataType)) {
		//			case AmiDatasourceColumn.TYPE_BIGDEC:
		//			case AmiDatasourceColumn.TYPE_BIGINT:
		//			case AmiDatasourceColumn.TYPE_BOOLEAN:
		//			case AmiDatasourceColumn.TYPE_BYTE:
		//			case AmiDatasourceColumn.TYPE_CHAR:
		//			case AmiDatasourceColumn.TYPE_COMPLEX:
		//			case AmiDatasourceColumn.TYPE_DOUBLE:
		//			case AmiDatasourceColumn.TYPE_FLOAT:
		//			case AmiDatasourceColumn.TYPE_INT:
		//			case AmiDatasourceColumn.TYPE_LONG:
		//			case AmiDatasourceColumn.TYPE_SHORT:
		//			case AmiDatasourceColumn.TYPE_UTC:
		//			case AmiDatasourceColumn.TYPE_UTCN:
		//			case AmiDatasourceColumn.TYPE_UUID:
		//				setNoBroadCast(m);
		//				//enable common options
		//				this.columnMetaDataEditForm.disableCommonOptions(false);
		//				break;
		//			case AmiDatasourceColumn.TYPE_STRING:
		//				setNoBroadCast(m);
		//				//enable common options
		//				this.columnMetaDataEditForm.disableCommonOptions(false);
		//
		//				Boolean isCompact = Caster_Boolean.INSTANCE.cast(m.get(AmiConsts.COMPACT));
		//				Boolean isAscii = Caster_Boolean.INSTANCE.cast(m.get(AmiConsts.ASCII));
		//				Boolean isBitmap = Caster_Boolean.INSTANCE.cast(m.get(AmiConsts.BITMAP));
		//				Boolean isOndisk = Caster_Boolean.INSTANCE.cast(m.get(AmiConsts.ONDISK));
		//				Boolean isEnum = Caster_Boolean.INSTANCE.cast(m.get(AmiConsts.TYPE_NAME_ENUM));
		//				boolean isCache = m.get(AmiConsts.CACHE) != null;
		//				if (isCache) {
		//					getColumnOptionEditField(AmiConsts.CACHE).setValue(true).setDisabled(false);
		//					String rawCacheValue = (String) m.get(AmiConsts.CACHE);
		//					int cacheValue = parseCacheValue(rawCacheValue).getA();
		//					String cacheUnit = parseCacheValue(rawCacheValue).getB();
		//					if (SH.isnt(cacheUnit))
		//						cacheUnit = AmiConsts.CACHE_UNIT_DEFAULT_BYTE;
		//					byte cacheUnitByte = AmiCenterManagerUtils.toCacheUnitCode(cacheUnit);
		//					this.columnMetaDataEditForm.getCacheValueField().setValue(SH.toString(cacheValue));
		//					this.columnMetaDataEditForm.getCacheUnitField().setValue(cacheUnitByte);
		//				}
		//				//enable edit for all string options
		//				this.columnMetaDataEditForm.disableStringOptions(false);
		//				if (isCompact != null && Boolean.TRUE.equals(isCompact)) {
		//					getColumnOptionEditField(AmiConsts.COMPACT).setValue(true).setDisabled(false);
		//					getColumnOptionEditField(AmiConsts.BITMAP).setDisabled(true);
		//				}
		//				if (isAscii != null && Boolean.TRUE.equals(isAscii)) {
		//					getColumnOptionEditField(AmiConsts.ASCII).setValue(true).setDisabled(false);
		//				}
		//				if (isBitmap != null && Boolean.TRUE.equals(isBitmap)) {
		//					getColumnOptionEditField(AmiConsts.BITMAP).setValue(true).setDisabled(false);
		//					getColumnOptionEditField(AmiConsts.COMPACT).setDisabled(true);
		//
		//				}
		//				if (isOndisk != null && Boolean.TRUE.equals(isOndisk)) {
		//					getColumnOptionEditField(AmiConsts.ONDISK).setValue(true).setDisabled(false);
		//				}
		//				if (isEnum != null && Boolean.TRUE.equals(isEnum)) {
		//					getColumnOptionEditField(AmiConsts.TYPE_NAME_ENUM).setValue(true).setDisabled(false);
		//				}
		//
		//				break;
		//			case AmiDatasourceColumn.TYPE_BINARY:
		//				setNoBroadCast(m);
		//				//enable common options
		//				this.columnMetaDataEditForm.disableCommonOptions(false);
		//				break;
		//			default:
		//				throw new NullPointerException();
		//		}
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
		sb.append("CREATE PUBLIC TABLE ").append(tableNameField.getValue()).append('(');
		//schema
		Table t = this.columnMetadata.getTable().getTable();
		Iterator<Row> iter = t.getRows().iterator();
		while (iter.hasNext()) {
			Row row = iter.next();
			String dataType = (String) row.get("dataType");
			String columnName = (String) row.get("columnName");
			sb.append(columnName).append(" ").append(dataType);

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
				sb.append(' ').append("Cache = ");
			String cacheVal = (String) row.get("cacheValue");
			if (SH.is(cacheVal))
				sb.append(' ').append(SH.doubleQuote(cacheVal));
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
	}

	@Override
	public void enableEdit(boolean enable) {
		for (FormPortletField<?> fpf : this.tableInfoPortlet.getFormFields()) {
			if (fpf != this.enableEditingCheckbox)
				fpf.setDisabled(!enable);
		}
		enableColumnEditing(enable);

	}

	@Override
	public void onTableEditComplete(Table origTable, Table editedTable, FastTablePortlet fastTablePortlet, StringBuilder errorSink) {
		if (errorSink.length() > 0) {
			this.columnMetadata.finishEdit();
			getManager().showAlert(errorSink.toString());
			return;
		}
		//TODO:need to calculate new position for the added row

		if (editedTable.getSize() == 0) {
			this.columnMetadata.finishEdit();
			return;
		}
		this.columnMetadata.finishEdit();
		if (editedTable.getRows().size() != 1)
			throw new UnsupportedOperationException("Only one row is allowed to be edited at a time");
		Row r = editedTable.getRow(0);
		Row origRow = origTable.getRow(0);
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
		//update the log table if the column name has changed for added empty row
		if(isAdd) {
			String colNameNuw = (String) r.get("columnName");
			String colNameOld = (String) origRow.get("columnName");
			if(!colNameNuw.equals(colNameOld))
				onEmptyRowUpdated(colNameNuw, colNameOld);
		}
		//check mutual exclusive column options
		Boolean isCompact = Caster_Boolean.INSTANCE.cast(r.get("compact"));
		Boolean isAscii = Caster_Boolean.INSTANCE.cast(r.get("ascii"));
		Boolean isBitmap = Caster_Boolean.INSTANCE.cast(r.get("bitmap"));
		Boolean isOndisk = Caster_Boolean.INSTANCE.cast(r.get("ondisk"));
		Boolean isEnum = Caster_Boolean.INSTANCE.cast(r.get("enum"));
		Boolean isCache = Caster_Boolean.INSTANCE.cast(r.get("cache"));
		String type =(String) r.get("dataType");
		//RULE1: ONDISK can not be used in conjunction with other supplied directives,aka (isOndisk && (isAscii || isBitmap || isEnum)) should be disallowed
		if(isOndisk && (isAscii || isBitmap || isEnum))
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING,  (String) r.get("columnName"), "ONDISK can not be used in conjunction with other supplied directives");
		
		//RULE2: BITMAP and COMPACT directive are mutually exclusive, aka disallow (isCompact && isBitmap)
		if(isCompact && isBitmap)
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING,  (String) r.get("columnName"), "BITMAP and COMPACT directive are mutually exclusive");


		//RULE3: ASCII directive only supported for STRING columns with COMPACT option, aka disallow (isAscii && !isCompact)
		if(isAscii && !isCompact)
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING,  (String) r.get("columnName"), "ASCII directive only supported for STRING columns with COMPACT option");

		//RULE4: ONDISK directive only supported for STRING and BINARY columns
		if(isOndisk && !"String".equalsIgnoreCase(type) && !"Binary".equalsIgnoreCase(type))
			userLogTable.addRow(AmiUserEditMessage.ACTION_TYPE_WARNING,  (String) r.get("columnName"), "ONDISK directive only supported for STRING and BINARY columns");

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

	@Override
	public String previewEdit() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void revertEdit() {
		super.revertEdit();
		//also revert the table
		importFromText(sql, new StringBuilder());

	}
	
//	public static Map<String,Object> retrieveColumnProperty(Row r){
//		
//	}
	
	public static String getOptionStringForRow(Row r) {
		StringBuilder sb = new StringBuilder();
		Boolean isCompact = Caster_Boolean.INSTANCE.cast(r.get("compact"));
		Boolean isAscii = Caster_Boolean.INSTANCE.cast(r.get("ascii"));
		Boolean isBitmap = Caster_Boolean.INSTANCE.cast(r.get("bitmap"));
		Boolean isOndisk = Caster_Boolean.INSTANCE.cast(r.get("ondisk"));
		Boolean isEnum = Caster_Boolean.INSTANCE.cast(r.get("enum"));
		Boolean isCache = Caster_Boolean.INSTANCE.cast(r.get("cache"));
		if(isCompact)
			sb.append("Compact ");
		if(isAscii)
			sb.append("Ascii");
		if(isBitmap)
			sb.append("Bitmap");
		if(isOndisk)
			sb.append("Ondisk");
		if(isEnum)
			sb.append("Enum");
		if(isCache)
			sb.append("Cache");
		if(isCache) {
			String cacheValue = Caster_String.INSTANCE.cast(r.get("cacheValue"));
			if(cacheValue != null)
				sb.append("=").append(cacheValue);
		}
		return sb.toString();
			
	}

}
