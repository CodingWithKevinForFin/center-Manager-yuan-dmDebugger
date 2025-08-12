package com.f1.ami.web.centermanager.editors.triggers.smarteditors;

import java.util.Map;
import java.util.Set;

import com.f1.ami.web.AmiWebMenuUtils;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.editors.triggers.AmiCenterManagerTriggerEditor_JoinTrigger;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenuLink;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletButtonField;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuFactory;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuListener;
import com.f1.suite.web.portal.impl.form.FormPortletDivField;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextAreaField;
import com.f1.suite.web.portal.impl.form.FormPortletTitleField;
import com.f1.utils.CH;
import com.f1.utils.SH;

public class AmiCenterManagerTriggerEditor_JoinSelectEditor extends FormPortlet implements FormPortletListener, FormPortletContextMenuFactory, FormPortletContextMenuListener {
	final private FormPortletTitleField selectTitleField;
	final private FormPortletSelectField<String> targetColumnField;

	final private FormPortletTextAreaField sourceColumnField;

	final private FormPortletTextAreaField outputField;
	final private FormPortletButtonField addButton;
	final private FormPortletButtonField clearButton;
	public StringBuilder output = new StringBuilder();

	final private AmiWebService service;

	private AmiCenterManagerTriggerEditor_JoinTrigger owner;

	private static final int COLNAME_WIDTH = 150; //60
	private static final int DEFAULT_ROWHEIGHT = 25;
	private static final int DEFAULT_LEFTPOS = 80; //164
	private static final int DEFAULT_SPACING = 10;
	private static final int DEFAULT_TITLEHEIGHT = 27;
	private static final int DEFAULT_TOPPOS = DEFAULT_SPACING + DEFAULT_TITLEHEIGHT;

	public AmiCenterManagerTriggerEditor_JoinSelectEditor(PortletConfig config, final AmiCenterManagerTriggerEditor_JoinTrigger owner) {
		super(config);
		this.owner = owner;
		this.service = AmiWebUtils.getService(getManager());
		this.selectTitleField = this.addField(new FormPortletTitleField("selects"));
		this.selectTitleField.setLeftPosPx(400);
		this.selectTitleField.setHelp(("An expression for how to relate the two tables in the form:" + "<br>"
				+ "<b><i style=\"color:blue\">leftColumn == rightColumn [ && leftColumn == rightColumn ... ]</i></b>"));

		FormPortletDivField sourceTitleDiv = this.addField(new FormPortletDivField("<b> Source </b>"));
		FormPortletDivField targetTitleDiv = this.addField(new FormPortletDivField("<b> Target </b>"));

		targetTitleDiv.setLeftPosPx(DEFAULT_LEFTPOS + 45).setTopPosPx(DEFAULT_TOPPOS).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(200);
		sourceTitleDiv.setLeftPosPx(DEFAULT_LEFTPOS + COLNAME_WIDTH + DEFAULT_LEFTPOS + 48).setTopPosPx(DEFAULT_TOPPOS).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(200);

		targetColumnField = this.addField(new FormPortletSelectField(String.class, ""));
		targetColumnField.setWidthPx(COLNAME_WIDTH);
		targetColumnField.setHeightPx(DEFAULT_ROWHEIGHT);
		targetColumnField.setLeftPosPx(DEFAULT_LEFTPOS);
		targetColumnField.setTopPosPx(DEFAULT_TOPPOS * 2);
		targetColumnField.setName(AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED);

		sourceColumnField = this.addField(new FormPortletTextAreaField("=&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp"));
		sourceColumnField.setWidthPx(COLNAME_WIDTH * 3);
		sourceColumnField.setLeftPosPx(DEFAULT_LEFTPOS + COLNAME_WIDTH + DEFAULT_LEFTPOS);
		sourceColumnField.setTopPosPx(DEFAULT_TOPPOS * 2);
		sourceColumnField.setHeightPx(DEFAULT_ROWHEIGHT * 4);

		sourceColumnField.setHasButton(true);
		sourceColumnField.setName(AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED);
		sourceColumnField.setCorrelationData(new Formula() {
			@Override
			public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
				BasicWebMenu r = new BasicWebMenu();
				AmiWebMenuUtils.createOperatorsMenu(r, AmiWebUtils.getService(getManager()), "");

				if (owner.getLeftTable() != null && owner.getRightTable() != null && owner.getLeftTableColumns() != null && owner.getRightTableColumns() != null) {
					WebMenu tableNamesAndColumns = new BasicWebMenu("Table Names && Columns", true);

					//TODO: better action than "conq_" for?(color no quotes)
					WebMenu leftTableCols = new BasicWebMenu(owner.getLeftTable() + "&nbsp;&nbsp;&nbsp;<i>Left Table</i>", true);
					for (String c : CH.sort(owner.getLeftTableColumns(), SH.COMPARATOR_CASEINSENSITIVE_STRING))
						leftTableCols.add(new BasicWebMenuLink(c, true, "conq_" + owner.getLeftTable() + "." + c).setAutoclose(false).setCssStyle("_fm=courier"));

					WebMenu rightTableCols = new BasicWebMenu(owner.getRightTable() + "&nbsp;&nbsp;&nbsp;<i>Right Table</i>", true);
					for (String c : CH.sort(owner.getRightTableColumns(), SH.COMPARATOR_CASEINSENSITIVE_STRING))
						rightTableCols.add(new BasicWebMenuLink(c, true, "conq_" + owner.getRightTable() + "." + c).setAutoclose(false).setCssStyle("_fm=courier"));

					tableNamesAndColumns.add(leftTableCols);
					tableNamesAndColumns.add(rightTableCols);
					r.add(tableNamesAndColumns);
				}

				if (owner.getLeftTable() != null && owner.getRightTable() != null) {
					//table names [left, right]
					WebMenu tableNames = new BasicWebMenu("Table Names", true);
					String leftTableHTML = owner.getLeftTable() + "&nbsp;&nbsp;&nbsp;<i>Left Table</i>";
					String rightTableHTML = owner.getRightTable() + "&nbsp;&nbsp;&nbsp;<i>Right Table</i>";
					tableNames.add(new BasicWebMenuLink(leftTableHTML, true, "var_" + owner.getLeftTable()).setAutoclose(false).setCssStyle("_fm=courier"));
					tableNames.add(new BasicWebMenuLink(rightTableHTML, true, "var_" + owner.getRightTable()).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(tableNames);
				}

				if (owner.getLeftTableColumns() != null) {
					//table columns [leftColumn, rightColumn]
					WebMenu leftColumns = new BasicWebMenu("Left Table Columns", true);
					for (String c : CH.sort(owner.getLeftTableColumns(), SH.COMPARATOR_CASEINSENSITIVE_STRING))
						leftColumns.add(new BasicWebMenuLink(c, true, "var_" + c).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(leftColumns);
				}
				if (owner.getRightTableColumns() != null) {
					WebMenu rightColumns = new BasicWebMenu("Right Table Columns", true);
					for (String c : CH.sort(owner.getRightTableColumns(), SH.COMPARATOR_CASEINSENSITIVE_STRING))
						rightColumns.add(new BasicWebMenuLink(c, true, "var_" + c).setAutoclose(false).setCssStyle("_fm=courier"));
					r.add(rightColumns);
				}

				return r;
			}

			@Override
			public void onContextMenu(FormPortletField field, String action) {
				AmiWebMenuUtils.processContextMenuAction(AmiWebUtils.getService(getManager()), action, field);

			}
		});

		this.addButton = this.addField(new FormPortletButtonField("").setValue("Add"));
		this.addButton.setLeftPosPx(DEFAULT_LEFTPOS + COLNAME_WIDTH + DEFAULT_LEFTPOS + 230).setTopPosPx(DEFAULT_TOPPOS * 5).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(80);
		this.addButton.setCssStyle("_fm=bold|_fg=#FFFFFF|_bg=#FFA500|style.borderRadius=5px");
		//this.addButton.setGroupName(AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED);

		this.clearButton = this.addField(new FormPortletButtonField("").setValue("Clear"));
		this.clearButton.setLeftPosPx(DEFAULT_LEFTPOS + COLNAME_WIDTH + DEFAULT_LEFTPOS + 320).setTopPosPx(DEFAULT_TOPPOS * 5).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(80);
		//this.clearButton.setGroupName(AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED);

		this.outputField = this.addField(new FormPortletTextAreaField("Output"));
		this.outputField.setLeftPosPx(DEFAULT_LEFTPOS).setTopPosPx(DEFAULT_TOPPOS * 4 + DEFAULT_ROWHEIGHT * 4).setHeightPx(DEFAULT_ROWHEIGHT * 5).setWidthPx(600);
		this.outputField.setName("output");

		this.addFormPortletListener(this);
		this.addMenuListener(this);
		this.setMenuFactory(this);
	}

	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField node) {
		if (node == this.sourceColumnField) {
			Formula f = (Formula) this.sourceColumnField.getCorrelationData();
			f.onContextMenu(node, action);
		}

	}

	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		if (field == this.sourceColumnField) {
			Formula f = (Formula) this.sourceColumnField.getCorrelationData();
			return f.createMenu(formPortlet, field, cursorPosition);
		}
		return null;
	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		if (field == this.addButton) {
			addSelectClause();
		} else if (field == this.clearButton) {
			clearSelectClause();
		} else if (field == this.outputField) {
			onOutPutFieldChanged(field);
		}

	}

	public void onOutPutFieldChanged(FormPortletField<?> field) {
		this.output.setLength(0);
		this.output.append(field.getValue());
	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

	}

	public static interface Formula {
		public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition);
		public void onContextMenu(FormPortletField field, String action);
	}

	//add
	public static WebMenu createVariablesMenu(String menuName, Set<String> columns) {
		WebMenu variables = new BasicWebMenu(menuName, true);

		for (String column : CH.sort(columns, SH.COMPARATOR_CASEINSENSITIVE_STRING)) {
			variables.add(new BasicWebMenuLink(column, true, "var_" + column).setAutoclose(false).setCssStyle("_fm=courier"));
		}
		return variables;
	}

	public void onResultTableColumnsChanged() {
		targetColumnField.clearOptions();
		for (String col : this.owner.getResultTableColumns())
			targetColumnField.addOption(col, col);
	}

	public void addSelectClause() {
		String targetColumn = this.targetColumnField.getValue();
		String sourceColumn = this.sourceColumnField.getValue();
		if (this.output.length() == 0)
			this.output.append(targetColumn).append(" = ").append(sourceColumn);
		else
			this.output.append(',').append(targetColumn).append(" = ").append(sourceColumn);
		this.outputField.setValue(this.output.toString());
		owner.getMainEditor().onFieldChanged(outputField);
	}

	public void clearSelectClause() {
		this.output.setLength(0);
		this.outputField.setValue("");
		owner.getMainEditor().onFieldChanged(outputField);
	}

	public String getOutput() {
		return outputField.getValue();
	}

	public FormPortletField<?> getOutputField() {
		return outputField;
	}

}
