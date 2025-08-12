package com.f1.ami.web.centermanager.editors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenuLink;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.PortletMetrics;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextEditField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.suite.web.portal.impl.form.FormPortletTitleField;
import com.f1.utils.CH;
import com.f1.utils.SH;

public class AmiCenterManagerIndexConfigForm extends FormPortlet implements FormPortletListener {
	private static final int DEFAULT_ROWHEIGHT = 25;
	private static final int DEFAULT_LEFTPOS = 80; //164
	private static final int DEFAULT_TITLELEFTPOS = 164; //164
	private static final int DEFAULT_SPACING = 10;
	private static final int DEFAULT_TITLEHEIGHT = 27;
	private static final int DEFAULT_TITLEWIDTH = 200;
	private static final int DEFAULT_TOPPOS = DEFAULT_SPACING + DEFAULT_TITLEHEIGHT;
	private static final int DEFAULT_COLNAME_WIDTH = 190; //60

	public static final String TYPE_COLNAME = "COLUMN NAME";
	public static final String TYPE_INDEXTYPE = "INDEX TYPE";

	private FormPortletTitleField indexConfigTitleField;
	private List<FormPortletSelectField> indexTypeFields;
	private List<FormPortletTextField> colNameFields;
	private AmiCenterManagerOptionFieldListener listener;

	private int size;

	public AmiCenterManagerIndexConfigForm(AmiCenterManagerOptionFieldListener listener, PortletConfig config) {
		super(config);
		this.setListener(listener);
		this.setSize(0);
		this.indexTypeFields = new ArrayList<FormPortletSelectField>();
		this.colNameFields = new ArrayList<FormPortletTextField>();
		this.indexConfigTitleField = new FormPortletTitleField("Index Configuration");

		this.addField(indexConfigTitleField);
		this.addFormPortletListener(this);

	}

	public void initIndexField() {
		this.addIndexField();
	}

	public void resetIndexFields() {
		int temp = this.getSize();
		if (temp == 0)
			return;
		for (int i = temp - 1; i >= 0; i--) {
			removeIndexFieldAtPos(i);
		}

	}

	private void repositionAtPosition(int position) {
		FormPortletTextField colNameField = this.colNameFields.get(position);
		FormPortletSelectField indexTypeField = this.indexTypeFields.get(position);

		colNameField.setWidthPx(DEFAULT_COLNAME_WIDTH);
		colNameField.setHeightPx(DEFAULT_ROWHEIGHT);
		colNameField.setLeftPosPx(DEFAULT_LEFTPOS);
		colNameField.setTopPosPx(DEFAULT_TOPPOS + (DEFAULT_ROWHEIGHT + DEFAULT_SPACING) * position);

		indexTypeField.setLeftPosPx(DEFAULT_LEFTPOS + DEFAULT_COLNAME_WIDTH + DEFAULT_LEFTPOS);
		indexTypeField.setTopPosPx(DEFAULT_TOPPOS + (DEFAULT_ROWHEIGHT + DEFAULT_SPACING) * position);
		indexTypeField.setRightPosPx(50);
		indexTypeField.setHeightPx(DEFAULT_ROWHEIGHT);
	}

	public void addIndexField() {
		addIndexFieldAtPos(getSize());
	}

	//	//first field is titlefield(index 0)
	//	@Deprecated
	//	public Tuple2<FormPortletTextField, FormPortletSelectField> getIndexFieldPairAt(int position) {
	//		Tuple2<FormPortletTextField, FormPortletSelectField> pair = new Tuple2<FormPortletTextField, FormPortletSelectField>();
	//		FormPortletTextField colName = (FormPortletTextField) this.getFieldAt(position * 2 + 2);
	//		FormPortletSelectField<Short> indexType = (FormPortletSelectField) this.getFieldAt(position * 2 + 1);
	//		pair.setA(colName);
	//		pair.setB(indexType);
	//		return pair;
	//	}

	public FormPortletTextField getIndexColumnNameAt(int position) {
		return this.colNameFields.get(position);
	}

	public FormPortletSelectField getIndexTypeAt(int position) {
		return this.indexTypeFields.get(position);
	}

	public void addIndexFieldAtPos(int pos) {
		FormPortletTextField colNameField = new FormPortletTextField("COLUMN" + AmiCenterEntityConsts.REQUIRED_FIELD_ANNOTATION_HTML).setName(TYPE_COLNAME).setValue("");
		FormPortletSelectField<Short> indexTypeField = new FormPortletSelectField(Short.class, "TYPE" + AmiCenterEntityConsts.REQUIRED_FIELD_ANNOTATION_HTML)
				.setName(TYPE_INDEXTYPE);
		indexTypeField.setHasButton(true);
		indexTypeField.addOption(AmiCenterEntityConsts.INDEX_TYPE_CODE_SORT, AmiCenterEntityConsts.INDEX_TYPE_SORT);
		indexTypeField.addOption(AmiCenterEntityConsts.INDEX_TYPE_CODE_HASH, AmiCenterEntityConsts.INDEX_TYPE_HASH);
		indexTypeField.addOption(AmiCenterEntityConsts.INDEX_TYPE_CODE_SERIES, AmiCenterEntityConsts.INDEX_TYPE_SERIES);

		colNameFields.add(pos, colNameField);
		indexTypeFields.add(pos, indexTypeField);

		//Order of fields being added changes if you can select a field.
		this.addField(indexTypeField);
		this.addField(colNameField);

		colNameField.setHasButton(true);

		this.setSize(this.getSize() + 1);
		this.repositionFromPosition(0);
		this.listener.onOptionFieldAdded();
	}
	private void removeIndexFieldAtPos(int position) {
		FormPortletTextField colNameField = this.colNameFields.remove(position);
		FormPortletSelectField<Short> indexTypeField = this.indexTypeFields.remove(position);

		this.setSize(getSize() - 1);
		this.repositionFromPosition(position);

		this.removeField(colNameField);
		this.removeField(indexTypeField);
		this.listener.onOptionFieldRemoved();
	}

	@Override
	public int getSuggestedHeight(PortletMetrics pm) {
		return DEFAULT_SPACING * 1 + size * (DEFAULT_ROWHEIGHT + DEFAULT_SPACING) + DEFAULT_TITLEHEIGHT;
	}

	//TODO:is this necessary?
	private void addColname(int pos) {
		//		String name = whereClauseObject.getVarName();
		//		whereClauseObject.setVarName("");
		//		this.addVarnameNoFire(name, pos);
		//		this.colNameFields.get(pos).setValue(this.whereClauseObjectsList.get(pos).getVarName());
	}

	private void repositionFromPosition(int position) {
		for (int i = position; i < getSize(); i++) {
			repositionAtPosition(i);
		}
	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		// TODO Auto-generated method stub

	}

	public int getSize() {
		return this.size;
	}

	private void setSize(int size) {
		this.size = size;
	}

	//There is only one listener
	protected void setListener(AmiCenterManagerOptionFieldListener listener) {
		if (this.listener != null)
			return;
		this.listener = listener;
	}

	//There is only one listener
	protected void removeListener(AmiCenterManagerOptionFieldListener listener) {
		if (this.listener == null)
			return;
		this.listener = null;
	}

	public void createIndexFieldsContextMenu(FormPortletField field, WebMenu sink) {
		int pos = this.indexTypeFields.indexOf(field);
		boolean enable_del = this.getSize() > 1;
		if (pos >= 0) {
			sink.add(new BasicWebMenuLink("Add Index ...", true, "add_subindex" + pos));
			sink.add(new BasicWebMenuLink("Remove Index ...", enable_del, "remove_subindex" + pos));
		}
	}

	public void createColNameFieldsContextMenu(FormPortletField field, WebMenu sink, Iterable<String> colNames) {
		int pos = this.colNameFields.indexOf(field);
		String prefix = "colvar_";
		if (pos >= 0) {
			for (String var : CH.sort(colNames, SH.COMPARATOR_CASEINSENSITIVE))
				sink.add(new BasicWebMenuLink(var, true, "add_" + prefix + var).setCssStyle("_fm=courier"));
		}
	}

	public void onIndexFieldsFormContextMenu(String action, FormPortletField field) {
		if (action.startsWith("add_subindex")) {
			int position = SH.parseInt(SH.stripPrefix(action, "add_subindex", true));
			this.addIndexFieldAtPos(position + 1);
		} else if (action.startsWith("remove_subindex")) {
			int position = SH.parseInt(SH.stripPrefix(action, "remove_subindex", true));
			this.removeIndexFieldAtPos(position);
		} else {
			String columnName = SH.stripPrefix(action, "add_colvar_", true);
			((FormPortletTextEditField) field).setValue("");
			((FormPortletTextEditField) field).insertAtCursor(columnName);
			this.listener.onOptionFieldEdited(field);
		}

	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

	}

}
