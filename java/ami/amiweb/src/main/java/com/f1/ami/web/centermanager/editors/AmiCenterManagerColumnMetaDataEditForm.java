package com.f1.ami.web.centermanager.editors;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.f1.ami.amicommon.AmiConsts;
import com.f1.ami.amicommon.msg.AmiDatasourceColumn;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.PortletManager;
import com.f1.suite.web.portal.impl.ConfirmDialogPortlet;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletCheckboxField;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuFactory;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuListener;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.suite.web.portal.impl.form.FormPortletTitleField;
import com.f1.suite.web.portal.style.PortletStyleManager_Dialog;
import com.f1.utils.OH;
import com.f1.utils.OneToOne;
import com.f1.utils.SH;
import com.f1.utils.casters.Caster_String;
import com.f1.utils.concurrent.IdentityHashSet;
import com.f1.utils.structs.Tuple2;

public class AmiCenterManagerColumnMetaDataEditForm extends GridPortlet implements FormPortletListener, FormPortletContextMenuListener, FormPortletContextMenuFactory {
	public static HashMap<String, String> KEY_TO_NAME_MAP = new HashMap<String, String>();
	public static String VARNAME_COLUMN_DATA_TYPE = "dataType";
	public static String VARNAME_COLUMN_NAME = "columnName";
	public static String VARNAME_NONULL = "noNull";
	public static String VARNAME_CACHE_UNIT = "CacheUnit";
	public static String VARNAME_CACHE_VALUE = "CacheValue";
	public static OneToOne<String, String> KEY_TO_NAME_MAPPING;
	public final static byte MODE_ADD = 0;
	public final static byte MODE_EDIT = 1;

	//Managing add/drop column
	public final static byte ACTION_ADD_BEFORE = 0;
	public final static byte ACTION_ADD_AFTER = 1;
	public final static byte ACTION_DROP = 2;
	private byte actionMode = -1;
	private String targetColumnName = null;

	public static String WARNING_ON_ONDISK = "ONDISK can not be used in conjunction with other supplied directives";

	static {
		KEY_TO_NAME_MAP.put("columnName", "Column Name");
		KEY_TO_NAME_MAP.put("dataType", "Data Type");
		KEY_TO_NAME_MAPPING = new OneToOne(KEY_TO_NAME_MAP);
	}
	final private FormPortlet form;
	final private AmiWebService service;

	private FormPortletTitleField columnTitleField;
	private FormPortletTextField columnNameEditField;
	private FormPortletSelectField<Byte> dataTypeEditField;

	private FormPortletTitleField storageTitleField;

	private FormPortletCheckboxField noNullEditField;
	private FormPortletCheckboxField broadcastEditField;

	private FormPortletCheckboxField isEnumField;
	private FormPortletCheckboxField isCacheField;
	private FormPortletTextField cacheValueField;
	private FormPortletSelectField<Byte> cacheUnitField;
	private FormPortletCheckboxField isCompactField;
	private FormPortletCheckboxField isAsciiField;
	private FormPortletCheckboxField isBitmapField;
	private FormPortletCheckboxField isOndiskField;
	private String tableName;
	private byte mode = MODE_EDIT;

	final private IdentityHashSet<FormPortletTextField> editedFields = new IdentityHashSet<FormPortletTextField>();
	final private IdentityHashSet<FormPortletCheckboxField> editedCheckboxFields = new IdentityHashSet<FormPortletCheckboxField>();
	final private IdentityHashSet<FormPortletSelectField> editedSelectFields = new IdentityHashSet<FormPortletSelectField>();

	//this cache is set when the row from the table is clicked
	private Map<String, String> columnCache = new HashMap<String, String>();
	private static final int LEFTPOS = 150;

	public AmiCenterManagerColumnMetaDataEditForm(PortletConfig config, String tableName, byte mode) {
		super(config);
		this.service = AmiWebUtils.getService(getManager());
		this.mode = mode;

		this.tableName = tableName;
		this.form = new FormPortlet(generateConfig());
		this.form.setMenuFactory(this);
		this.form.addMenuListener(this);
		this.form.addFormPortletListener(this);
		this.addChild(form);
		columnTitleField = form.addField(new FormPortletTitleField("COLUMN"));
		columnTitleField.setLeftPosPx(LEFTPOS).setTopPosPx(10).setWidthPx(400).setHeight(25);

		columnNameEditField = this.form.addField(new FormPortletTextField("Column Name" + AmiCenterEntityConsts.REQUIRED_FIELD_ANNOTATION_HTML));
		columnNameEditField.setName(VARNAME_COLUMN_NAME);
		columnNameEditField.setLeftPosPx(LEFTPOS).setTopPosPx(40).setWidthPx(400).setHeight(25);

		dataTypeEditField = this.form.addField(new FormPortletSelectField<Byte>(Byte.class, "Data Type" + AmiCenterEntityConsts.REQUIRED_FIELD_ANNOTATION_HTML));
		dataTypeEditField.setName(VARNAME_COLUMN_DATA_TYPE);
		dataTypeEditField.setLeftPosPx(LEFTPOS).setTopPosPx(80).setWidthPx(400).setHeight(25);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_NONE, "<NONE>");
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_STRING, AmiConsts.TYPE_NAME_STRING);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_BINARY, AmiConsts.TYPE_NAME_BINARY);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_BOOLEAN, AmiConsts.TYPE_NAME_BOOLEAN);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_DOUBLE, AmiConsts.TYPE_NAME_DOUBLE);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_FLOAT, AmiConsts.TYPE_NAME_FLOAT);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_UTC, AmiConsts.TYPE_NAME_UTC);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_UTCN, AmiConsts.TYPE_NAME_UTCN);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_LONG, AmiConsts.TYPE_NAME_LONG);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_INT, AmiConsts.TYPE_NAME_INTEGER);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_CHAR, AmiConsts.TYPE_NAME_CHAR);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_SHORT, AmiConsts.TYPE_NAME_SHORT);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_BYTE, AmiConsts.TYPE_NAME_BYTE);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_BIGINT, AmiConsts.TYPE_NAME_BIGINT);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_BIGDEC, AmiConsts.TYPE_NAME_BIGDEC);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_COMPLEX, AmiConsts.TYPE_NAME_COMPLEX);
		dataTypeEditField.addOption(AmiDatasourceColumn.TYPE_UUID, AmiConsts.TYPE_NAME_UUID);
		dataTypeEditField.setDefaultValue(AmiDatasourceColumn.TYPE_NONE);

		//add storage title
		int offset = 35;
		storageTitleField = this.form.addField(new FormPortletTitleField("STORAGE"));
		storageTitleField.setLeftPosPx(LEFTPOS).setTopPosPx(130 + offset - 10).setWidthPx(400).setHeight(25);
		//common fields
		//ROW1
		noNullEditField = new FormPortletCheckboxField(AmiConsts.NONULL);
		noNullEditField.setName("noNull");
		noNullEditField.setLeftPosPx(LEFTPOS - 30).setTopPosPx(160 + offset).setWidthPx(20).setHeight(20);
		broadcastEditField = new FormPortletCheckboxField(AmiConsts.NOBROADCAST);
		broadcastEditField.setName("nobroadcast");
		if (mode == MODE_ADD) {
			broadcastEditField.setDefaultValue(true);
			broadcastEditField.setValue(true);
		}
		this.broadcastEditField.setLeftPosPx(LEFTPOS + 110).setTopPosPx(160 + offset).setWidthPx(20).setHeight(20);
		if (mode == MODE_ADD) {
			columnNameEditField.setDisabled(false);
			dataTypeEditField.setDisabled(false);
			noNullEditField.setDisabled(false);
			broadcastEditField.setDisabled(false);
		} else {
			columnNameEditField.setDisabled(true);
			dataTypeEditField.setDisabled(true);
			noNullEditField.setDisabled(true);
			broadcastEditField.setDisabled(true);
		}

		this.form.addField(noNullEditField);
		this.form.addField(broadcastEditField);

		//Data type specific fields
		isEnumField = new FormPortletCheckboxField(AmiConsts.TYPE_NAME_ENUM);
		isEnumField.setName("enum");
		isEnumField.setLeftPosPx(LEFTPOS + 250).setTopPosPx(160 + offset).setWidthPx(20).setHeight(20);

		//ROW2
		isCompactField = new FormPortletCheckboxField(AmiConsts.COMPACT);
		isCompactField.setName("compact");
		isCompactField.setLeftPosPx(LEFTPOS - 30).setTopPosPx(200 + offset).setWidthPx(20).setHeight(20);
		isAsciiField = new FormPortletCheckboxField(AmiConsts.ASCII);
		isAsciiField.setName("ascii");
		isAsciiField.setLeftPosPx(LEFTPOS + 110).setTopPosPx(200 + offset).setWidthPx(20).setHeight(20);
		isBitmapField = new FormPortletCheckboxField(AmiConsts.BITMAP);
		isBitmapField.setName("bitmap");
		isBitmapField.setLeftPosPx(LEFTPOS + 250).setTopPosPx(200 + offset).setWidthPx(20).setHeight(20);

		//ROW3
		isOndiskField = new FormPortletCheckboxField(AmiConsts.ONDISK);
		isOndiskField.setName("ondisk");
		isOndiskField.setLeftPosPx(LEFTPOS - 30).setTopPosPx(240 + offset).setWidthPx(20).setHeight(20);

		isCacheField = new FormPortletCheckboxField(AmiConsts.CACHE);
		isCacheField.setName("cache");
		isCacheField.setCorrelationData(AmiConsts.CACHE);
		isCacheField.setLeftPosPx(LEFTPOS + 110).setTopPosPx(240 + offset).setWidthPx(20).setHeight(20);

		cacheValueField = new FormPortletTextField("");
		cacheValueField.setMaxChars(100);
		cacheValueField.setName("cacheValue");
		cacheValueField.setCorrelationData(AmiConsts.CACHE);
		cacheValueField.setLeftPosPx(LEFTPOS + 135).setTopPosPx(240 + offset).setWidthPx(70).setHeight(20);

		cacheUnitField = new FormPortletSelectField<Byte>(Byte.class, "Unit");
		cacheUnitField.setName(VARNAME_CACHE_UNIT);
		cacheUnitField.setCorrelationData(AmiConsts.CACHE);
		cacheUnitField.addOption(AmiConsts.CODE_CACHE_UNIT_DEFAULT_BYTE, AmiConsts.CACHE_UNIT_DEFAULT_BYTE);
		cacheUnitField.addOption(AmiConsts.CODE_CACHE_UNIT_KB, AmiConsts.CACHE_UNIT_KB);
		cacheUnitField.addOption(AmiConsts.CODE_CACHE_UNIT_MB, AmiConsts.CACHE_UNIT_MB);
		cacheUnitField.addOption(AmiConsts.CODE_CACHE_UNIT_GB, AmiConsts.CACHE_UNIT_GB);
		cacheUnitField.addOption(AmiConsts.CODE_CACHE_UNIT_TB, AmiConsts.CACHE_UNIT_TB);
		cacheUnitField.setDefaultValue(AmiConsts.CODE_CACHE_UNIT_DEFAULT_BYTE);
		cacheUnitField.setLeftPosPx(LEFTPOS + 220).setTopPosPx(240 + offset).setWidthPx(140).setHeight(20);

		this.form.addField(isEnumField);
		this.form.addField(isCompactField);
		this.form.addField(isAsciiField);
		this.form.addField(isBitmapField);
		this.form.addField(isOndiskField);
		this.form.addField(isCacheField);
		this.form.addField(cacheValueField);
		//not add cacheUnit for now
		//this.form.addField(cacheUnitField);

		//disable all the fields on init
		disableCache(true);
		disableAscii(true);
		disableOnDisk(true);
		disableBitmap(true);
		disableCompact(true);
		disableEnum(true);

	}

	public AmiCenterManagerColumnMetaDataEditForm(PortletConfig config, String tableName, byte mode, String targetColumnName, byte action) {
		this(config, tableName, mode);
		if (this.mode != MODE_ADD)
			throw new IllegalArgumentException();
		this.actionMode = action;
		this.targetColumnName = targetColumnName;

	}

	public FormPortletTextField getCacheValueField() {
		return this.cacheValueField;
	}

	public FormPortletSelectField<Byte> getCacheUnitField() {
		return this.cacheUnitField;
	}

	public void setColumnCache(String key, String value) {
		this.columnCache.put(key, value);
	}

	public FormPortlet getForm() {
		return this.form;
	}

	public void resetForm() {
		columnNameEditField.setDisabled(true);
		dataTypeEditField.setDisabled(true);
		dataTypeEditField.setDefaultValue(AmiDatasourceColumn.TYPE_NONE);
		noNullEditField.setDisabled(true).setValue(false);
		broadcastEditField.setDisabled(true).setValue(false);
		isCacheField.setDisabled(true).setValue(false);
		this.onIsCacheFieldChanged(isCacheField);
		cacheValueField.setDisabled(true).setValue("");
		cacheUnitField.setDisabled(true).setValue(AmiConsts.CODE_CACHE_UNIT_DEFAULT_BYTE);
		isOndiskField.setDisabled(true).setValue(false);
		isAsciiField.setDisabled(true).setValue(false);
		isBitmapField.setDisabled(true).setValue(false);
		isCompactField.setDisabled(true).setValue(false);
		isEnumField.setDisabled(true).setValue(false);
	}

	public void disableAllButCommonOptions(boolean disabled) {
		disableCache(disabled);
		disableOnDisk(disabled);
		disableAscii(disabled);
		disableBitmap(disabled);
		disableCompact(disabled);
		disableEnum(disabled);
		disableCommonOptions(!disabled);
	}

	public void disableAll(boolean disabled) {
		broadcastEditField.setDisabled(disabled);
		noNullEditField.setDisabled(disabled);
		disableCache(disabled);
		disableOnDisk(disabled);
		disableAscii(disabled);
		disableBitmap(disabled);
		disableCompact(disabled);
		disableEnum(disabled);
	}

	public void disableCache(boolean disabled) {
		isCacheField.setDisabled(disabled);
	}

	public void disableOnDisk(boolean disabled) {
		isOndiskField.setDisabled(disabled);
	}

	public void disableAscii(boolean disabled) {
		isAsciiField.setDisabled(disabled);
	}
	public void disableBitmap(boolean disabled) {
		isBitmapField.setDisabled(disabled);
	}
	public void disableCompact(boolean disabled) {
		isCompactField.setDisabled(disabled);
	}
	public void disableEnum(boolean disabled) {
		isEnumField.setDisabled(disabled);
	}

	public void disableBinaryOptions(boolean disabled) {
		disableCache(disabled);
	}

	public void disableStringOptions(boolean disabled) {
		disableCache(disabled);
		disableOnDisk(disabled);
		disableAscii(disabled);
		disableBitmap(disabled);
		disableCompact(disabled);
		disableEnum(disabled);
	}
	public void disableCommonOptions(boolean disabled) {
		broadcastEditField.setDisabled(disabled);
		noNullEditField.setDisabled(disabled);
	}

	//falsify an option when it is disabled
	public void onOptionDisabled(String option) {

	}

	public void onDataTypeChanged(byte dataType) {
		switch (dataType) {
			case AmiDatasourceColumn.TYPE_BIGDEC:
			case AmiDatasourceColumn.TYPE_BIGINT:
			case AmiDatasourceColumn.TYPE_BOOLEAN:
			case AmiDatasourceColumn.TYPE_BYTE:
			case AmiDatasourceColumn.TYPE_CHAR:
			case AmiDatasourceColumn.TYPE_COMPLEX:
			case AmiDatasourceColumn.TYPE_DOUBLE:
			case AmiDatasourceColumn.TYPE_FLOAT:
			case AmiDatasourceColumn.TYPE_INT:
			case AmiDatasourceColumn.TYPE_LONG:
			case AmiDatasourceColumn.TYPE_SHORT:
			case AmiDatasourceColumn.TYPE_UTC:
			case AmiDatasourceColumn.TYPE_UTCN:
			case AmiDatasourceColumn.TYPE_UUID:
				//enable common options
				disableCommonOptions(false);
				break;
			case AmiDatasourceColumn.TYPE_STRING:
				disableCommonOptions(false);
				isCompactField.setDisabled(false);
				onOptionDisabled("isCompact");

		}
	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		//		if (this.resetButton == button) {
		//			IdentityHashSet<FormPortletField> allEditedFields = new IdentityHashSet<FormPortletField>();
		//			for (FormPortletField ff : this.editedFields)
		//				allEditedFields.add(ff);
		//			for (FormPortletField ff : this.editedSelectFields)
		//				allEditedFields.add(ff);
		//			for (FormPortletField ff : this.editedCheckboxFields)
		//				allEditedFields.add(ff);
		//
		//			//loop over the edited fields and revert each of them
		//			for (FormPortletField f : allEditedFields) {
		//				boolean hasCacheChanged = false;
		//				if (f == this.cacheUnitField || f == this.cacheValueField || f == this.isCacheField)
		//					hasCacheChanged = true;
		//				String orig = null;
		//				if (hasCacheChanged) {
		//					String origCache = columnCache.get(AmiConsts.CACHE);
		//					//extract out the orignal cache component(whether it is unit or value or the flag
		//					if (f == this.cacheUnitField)
		//						orig = AmiCenterManagerEditColumnPortlet.parseCacheValue(origCache).getB();
		//					else if (f == this.isCacheField)
		//						orig = origCache == null ? "false" : "true";
		//					else if (f == this.cacheValueField)
		//						orig = SH.toString(AmiCenterManagerEditColumnPortlet.parseCacheValue(origCache).getA());
		//				} else
		//					orig = columnCache.get(f.getName());
		//				if (orig == null) {//the field is not configured prior to edit
		//					if (f instanceof FormPortletCheckboxField)
		//						f.setValue(false);
		//					else if (f instanceof FormPortletTextField)
		//						f.setValue("");//revert to empty string
		//					else if (f instanceof FormPortletSelectField) {
		//						if (VARNAME_CACHE_UNIT.equals(f.getName()))
		//							f.setValue(AmiConsts.CODE_CACHE_UNIT_DEFAULT_BYTE);
		//					}
		//					onFieldChanged(f);
		//				}
		//				if (f instanceof FormPortletSelectField) {
		//					FormPortletSelectField<Byte> sf = (FormPortletSelectField) f;
		//
		//					if (VARNAME_CACHE_UNIT.equals(sf.getName())) {
		//						short origTypeCode = AmiCenterManagerUtils.centerObjectTypeToCode(AmiCenterGraphNode.TYPE_TRIGGER, orig);
		//						if (SH.isnt(orig))
		//							orig = AmiConsts.CACHE_UNIT_DEFAULT_BYTE;
		//						sf.setValue(AmiCenterManagerUtils.toCacheUnitCode(orig));
		//						onFieldChanged(f);
		//					} else if (VARNAME_COLUMN_DATA_TYPE.equals(sf.getName())) {
		//						if (orig == null)
		//							orig = "<NONE>";
		//						sf.setValue(AmiCenterManagerUtils.toDataTypeCode(orig));
		//						onFieldChanged(f);
		//					}
		//				} else if (f instanceof FormPortletTextField) {
		//					FormPortletTextField tf = (FormPortletTextField) f;
		//					tf.setValue(orig);
		//					onFieldChanged(f);
		//				} else if (f instanceof FormPortletCheckboxField) {
		//					FormPortletCheckboxField cf = (FormPortletCheckboxField) f;
		//					if (orig == null)
		//						orig = "false";
		//					cf.setValue(Caster_Boolean.INSTANCE.cast(orig));
		//					onFieldChanged(f);
		//				}
		//			}
		//		} else if (this.submitButton == button) {
		//			//check datatype field and name field
		//			if (SH.isnt(this.columnNameEditField) || this.dataTypeEditField.getValue() == AmiDatasourceColumn.TYPE_NONE) {
		//				throwAlertDialogue("Column Name or Data type cannot be empty", "Warning");
		//				return;
		//			}
		//			String query = null;
		//			switch (this.mode) {
		//				case MODE_EDIT:
		//					//if only the name has changed
		//					if (this.editedFields.size() == 1 && this.editedFields.contains(this.columnNameEditField) && this.editedSelectFields.isEmpty()) {
		//						query = "ALTER TABLE " + this.tableName + " RENAME " + this.columnCache.get(VARNAME_COLUMN_NAME) + " TO " + this.columnNameEditField.getValue();
		//						getManager().showDialog("Submit Edit Column", new AmiCenterManagerSubmitEditScriptPortlet(this.service, generateConfig(), query),
		//								AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PORTLET_WIDTH, AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PORTLET_HEIGHT);
		//					} else {
		//						query = "ALTER TABLE " + this.tableName + " MODIFY " + this.columnCache.get(VARNAME_COLUMN_NAME) + " AS " + previewScript();
		//						getManager().showDialog("Submit Edit Column", new AmiCenterManagerSubmitEditScriptPortlet(this.service, generateConfig(), query),
		//								AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PORTLET_WIDTH, AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PORTLET_HEIGHT);
		//					}
		//					break;
		//				case MODE_ADD:
		//					switch (this.actionMode) {
		//						case ACTION_ADD_AFTER:
		//							query = "ALTER TABLE " + this.tableName + " ADD " + previewScript() + " AFTER " + this.targetColumnName;
		//							break;
		//						case ACTION_ADD_BEFORE:
		//							query = "ALTER TABLE " + this.tableName + " ADD " + previewScript() + " BEFORE " + this.targetColumnName;
		//							break;
		//						case ACTION_DROP://won't reach here, for drop column, no need to spin up a edit form
		//							query = "ALTER TABLE " + this.tableName + " DROP " + this.targetColumnName;
		//							break;
		//
		//					}
		//					getManager().showDialog("Submit Column", new AmiCenterManagerSubmitEditScriptPortlet(this.service, generateConfig(), query),
		//							AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PORTLET_WIDTH, AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PORTLET_HEIGHT);
		//					break;
		//				default:
		//					throw new NullPointerException("Unknow mode: " + this.mode);
		//			}
		//
		//		} else if (this.previewButton == button) {
		//			String text = AmiCenterManagerUtils.formatPreviewScript(previewScript());
		//			PortletStyleManager_Dialog dp = service.getPortletManager().getStyleManager().getDialogStyle();
		//			final PortletManager portletManager = service.getPortletManager();
		//			ConfirmDialogPortlet cdp = new ConfirmDialogPortlet(portletManager.generateConfig(), text, ConfirmDialogPortlet.TYPE_MESSAGE);
		//			int w = dp.getDialogWidth();
		//			int h = dp.getDialogHeight();
		//			portletManager.showDialog("Trigger Script", cdp, w + 200, h);
		//		} else if (this.diffButton == button) {
		//			Tuple2<Map<String, Object>, Map<String, Object>> diffs = getJsonDiff();
		//			LinkedHashMap a = new LinkedHashMap<String, Map>();
		//			a.put("Configuration", diffs.getA());
		//			LinkedHashMap b = new LinkedHashMap<String, Map>();
		//			b.put("Configuration", diffs.getB());
		//			//add command format
		//			a.put("Command", this.getOrigColumnCmd());
		//			b.put("Command", this.previewScript());
		//
		//			String oldConfig = AmiWebLayoutHelper.toJson(a, service);
		//			String newConfig = AmiWebLayoutHelper.toJson(b, service);
		//			AmiWebUtils.diffConfigurations(service, oldConfig, newConfig, "Orginal Script", "New Script", null);
		//		}
	}

	public String getOrigColumnCmd() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.columnCache.get(VARNAME_COLUMN_NAME)).append(" ").append(this.columnCache.get(VARNAME_COLUMN_DATA_TYPE)).append(" ");
		for (Entry<String, String> e : this.columnCache.entrySet()) {
			String key = e.getKey();
			String value = e.getValue();
			if (VARNAME_COLUMN_NAME.equals(key) || VARNAME_COLUMN_DATA_TYPE.equals(key))
				continue;
			if ("true".equals(value))
				sb.append(key).append(" ");
		}
		return sb.toString();
	}

	public void onDataTypeFieldChanged(FormPortletField<?> field) {
		if (AmiDatasourceColumn.TYPE_STRING == (byte) field.getValue())
			disableStringOptions(false);
		else if (AmiDatasourceColumn.TYPE_BINARY == (byte) field.getValue()) {
			disableAllButCommonOptions(true);
			disableBinaryOptions(false);
		} else {
			disableAllButCommonOptions(true);
		}
	}

	public void onIsCacheFieldChanged(FormPortletCheckboxField field) {
		if (field.getBooleanValue() == Boolean.FALSE) {
			cacheValueField.setDisabled(true);
			cacheUnitField.setDisabled(true);
		} else {
			cacheValueField.setDisabled(false);
			cacheUnitField.setDisabled(false);
		}

	}

	private Tuple2<Map<String, Object>, Map<String, Object>> getJsonDiff() {
		LinkedHashMap<String, Object> newConfig = new LinkedHashMap<String, Object>();
		LinkedHashMap<String, Object> oldConfig = new LinkedHashMap<String, Object>();
		for (FormPortletField f : this.editedFields) {
			String orig = this.columnCache.get(f.getName());
			if (orig == null) {//the field is not configured prior to edit
				newConfig.put(f.getName(), f.getValue());
			} else {
				newConfig.put(f.getName(), f.getValue());
				oldConfig.put(f.getName(), orig);
			}
		}

		for (FormPortletSelectField f : this.editedSelectFields) {
			String orig = this.columnCache.get(f.getName());
			if (orig == null) {//the field is not configured prior to edit
				newConfig.put(f.getName(), f.getOption(f.getValue()).getName());
			} else {
				newConfig.put(f.getName(), f.getOption(f.getValue()).getName());
				oldConfig.put(f.getName(), orig);
			}
		}

		for (FormPortletCheckboxField f : this.editedCheckboxFields) {
			String orig = this.columnCache.get(f.getName());
			if (orig == null) {//the field is not configured prior to edit
				newConfig.put(f.getName(), f.getValue());
			} else {
				newConfig.put(f.getName(), f.getValue());
				oldConfig.put(f.getName(), orig);
			}
		}

		return new Tuple2<Map<String, Object>, Map<String, Object>>(oldConfig, newConfig);
	}

	public void throwAlertDialogue(String warning, String dialogueTitle) {
		PortletStyleManager_Dialog dp = service.getPortletManager().getStyleManager().getDialogStyle();
		final PortletManager portletManager = service.getPortletManager();
		ConfirmDialogPortlet cdp = new ConfirmDialogPortlet(portletManager.generateConfig(), warning, ConfirmDialogPortlet.TYPE_MESSAGE);
		int w = dp.getDialogWidth();
		int h = dp.getDialogHeight();
		portletManager.showDialog(dialogueTitle, cdp, w + 200, h);
	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		onFieldChanged(field);

		//**PART1:Apply rules**
		//RULE0: If the data type field has changed, depending on what the new data type, disable/enable corresponding options
		if (field == this.dataTypeEditField) {
			onDataTypeFieldChanged(field);
		}
		if (field == this.isCacheField) {
			onIsCacheFieldChanged(isCacheField);
		}
		//RULE1: CACHE must be used in conjunction with ONDISK(triggered when hit apply)
		//RULE2: ONDISK can not be used in conjunction with other supplied directives(triggered when hit apply)
		//		if (field == this.isOndiskField && isOndiskField.getBooleanValue()) {
		//			disableStringOptions(true);
		//			this.isOndiskField.setDisabled(false);
		//		}
		//RULE3: BITMAP and COMPACT directive are mutually exclusive
		if (field == this.isBitmapField && this.isBitmapField.getBooleanValue()) {
			disableCompact(true);
		} else if (field == this.isBitmapField && !this.isBitmapField.getBooleanValue()) {
			disableCompact(false);
		}

		else if (field == this.isCompactField && this.isCompactField.getBooleanValue()) {
			disableBitmap(true);
		} else if (field == this.isCompactField && !this.isCompactField.getBooleanValue()) {
			disableBitmap(false);
		}
	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

	}

	private void onFieldChanged(FormPortletField field) {
		boolean hadNoChanges = this.editedFields.isEmpty() && this.editedSelectFields.isEmpty() && this.editedCheckboxFields.isEmpty();
		String value = null;
		Object rawValue = field.getValue();
		if (rawValue instanceof String)
			value = SH.trim((String) rawValue);
		else if (rawValue instanceof Byte) {// && (VARNAME_COLUMN_DATA_TYPE.equals(field.getName()) || VARNAME_COLUMN_DATA_TYPE.equals(field.getName()))) {
			Byte typeCode = (Byte) rawValue;
			FormPortletSelectField<Byte> sf = (FormPortletSelectField) field;
			value = sf.getOption(typeCode).getName();
		} else if (rawValue instanceof Boolean) {
			value = Caster_String.INSTANCE.cast(rawValue);
		}
		boolean hasCacheChanged = false;
		if (field == this.cacheUnitField || field == this.cacheValueField || field == this.isCacheField)
			hasCacheChanged = true;

		String orig = null;

		if (hasCacheChanged) {
			String origCache = columnCache.get(AmiConsts.CACHE);
			//extract out the orignal cache component(whether it is unit or value or the flag
			if (field == this.cacheUnitField) {
				orig = origCache == null ? null : AmiCenterManagerEditColumnPortlet.parseCacheValue(origCache).getB();
				if ("".equals(orig))
					orig = AmiConsts.CACHE_UNIT_DEFAULT_BYTE;
			}

			else if (field == this.isCacheField)
				orig = origCache == null ? "false" : "true";
			else if (field == this.cacheValueField)
				orig = origCache == null ? null : SH.toString(AmiCenterManagerEditColumnPortlet.parseCacheValue(origCache).getA());

		} else
			orig = columnCache.get(field.getName());
		if (field instanceof FormPortletTextField) {
			FormPortletTextField tf = (FormPortletTextField) field;
			if (OH.eq(value, orig) || (SH.isnt(field.getValue()) && orig == null)) {
				this.editedFields.remove(tf);
				if (!hasCacheChanged)
					formatFieldTitle(tf, false);
				else
					formatFieldTitle(this.isCacheField, false);
			} else {
				this.editedFields.add(tf);
				if (!hasCacheChanged)
					formatFieldTitle(tf, true);
				else
					formatFieldTitle(this.isCacheField, true);
			}
		} else if (field instanceof FormPortletSelectField) {
			FormPortletSelectField psf = (FormPortletSelectField) field;
			if (VARNAME_COLUMN_DATA_TYPE.equals(field.getName()))
				orig = "<NONE>";
			if (OH.eq(value, orig) || (SH.isnt(field.getValue()) && orig == null)) {
				this.editedSelectFields.remove(psf);
				if (!hasCacheChanged)
					formatFieldTitle(psf, false);
				else
					formatFieldTitle(this.isCacheField, false);
			} else {
				this.editedSelectFields.add(psf);
				if (!hasCacheChanged)
					formatFieldTitle(psf, true);
				else {
					//if any of the cache component field has changed, format isCacheField titie
					formatFieldTitle(this.isCacheField, true);
				}
			}
		} else if (field instanceof FormPortletCheckboxField) {
			FormPortletCheckboxField cf = (FormPortletCheckboxField) field;
			if (orig == null)
				orig = "false";
			if (OH.eq(value, orig) || (SH.isnt(field.getValue()) && orig == null)) {
				this.editedCheckboxFields.remove(cf);
				formatFieldTitle(cf, false);
			} else {
				this.editedCheckboxFields.add(cf);
				formatFieldTitle(cf, true);
			}
		}
		boolean hasNoChanges = this.editedFields.isEmpty() && this.editedSelectFields.isEmpty() && this.editedCheckboxFields.isEmpty();
		if (hasNoChanges != hadNoChanges) {
			//			this.submitButton.setEnabled(!hasNoChanges);
			//			this.resetButton.setEnabled(!hasNoChanges);
			//			this.diffButton.setEnabled(!hasNoChanges);
			//			this.previewButton.setEnabled(!hasNoChanges);
		}
	}

	private static void formatFieldTitle(FormPortletField<?> field, boolean hasChanged) {
		if (hasChanged)
			field.setTitle(AmiCenterEntityConsts.CHANGED_FIELD_ANNOTATION_HTML + field.getTitle());
		else
			field.setTitle(field.getTitle().replace(AmiCenterEntityConsts.CHANGED_FIELD_ANNOTATION_HTML, ""));

	}

	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField node) {
		// TODO Auto-generated method stub

	}

	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		// TODO Auto-generated method stub
		return null;
	}

	//returns "col1 datatype option"
	public String previewScript() {
		StringBuilder sb = new StringBuilder(this.columnNameEditField.getValue());
		sb.append(" ").append(this.dataTypeEditField.getOption(this.dataTypeEditField.getValue()).getName());
		//checking options
		//1. first check nonull and nobroadcast
		if (this.broadcastEditField.getBooleanValue())
			sb.append(" ").append(AmiConsts.NOBROADCAST);
		if (this.noNullEditField.getBooleanValue())
			sb.append(" ").append(AmiConsts.NONULL);

		//2. if binary or string, check ondisk and cache
		if (this.dataTypeEditField.getValue() == AmiDatasourceColumn.TYPE_BINARY || this.dataTypeEditField.getValue() == AmiDatasourceColumn.TYPE_STRING) {
			if (this.isOndiskField.getBooleanValue())
				sb.append(" ").append(AmiConsts.ONDISK);
			if (this.isCacheField.getBooleanValue()) {
				String unit = null;
				if (this.cacheUnitField.getValue() == AmiConsts.CODE_CACHE_UNIT_DEFAULT_BYTE)
					unit = "";
				else
					unit = this.cacheUnitField.getOption(this.cacheUnitField.getValue()).getName();
				sb.append(" ").append(AmiConsts.CACHE).append(" =\"").append(this.cacheValueField.getValue()).append(unit).append("\"");
			}

		}

		//3. lastly check String column alone
		if (this.dataTypeEditField.getValue() == AmiDatasourceColumn.TYPE_STRING) {
			if (this.isCompactField.getBooleanValue())
				sb.append(" ").append(AmiConsts.COMPACT);
			if (this.isAsciiField.getBooleanValue())
				sb.append(" ").append(AmiConsts.ASCII);
			if (this.isBitmapField.getBooleanValue())
				sb.append(" ").append(AmiConsts.BITMAP);
			if (this.isEnumField.getBooleanValue())
				sb.append(" ").append(AmiConsts.TYPE_NAME_ENUM);
		}

		return sb.toString();
	}

}
