package com.f1.ami.web.centermanager.editors.triggers.smarteditors;

import java.util.Map;
import java.util.Set;

import com.f1.ami.web.AmiWebMenuUtils;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.editors.triggers.AmiCenterManagerTriggerEditor_AggregateTrigger;
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
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.suite.web.portal.impl.form.FormPortletTitleField;
import com.f1.utils.CH;
import com.f1.utils.SH;

public class AmiCenterManagerTriggerEditor_AggregateSelectEditor extends FormPortlet implements FormPortletListener, FormPortletContextMenuFactory, FormPortletContextMenuListener {
	public static final Set<String> SUPPORTED_FUNC = CH.s("count", "countUnique", "first", "last", "max", "min", "stdev", "stdevs", "sum", "var");
	private FormPortletTitleField selectTitleField;

	public StringBuilder selectOutput = new StringBuilder();

	final private FormPortletSelectField<String> targetColumnField;
	final private FormPortletSelectField<String> aggFuncField;

	final private FormPortletTextField aggExpressionField;

	final private FormPortletTextAreaField outputField;
	final private FormPortletButtonField addButton;
	final private FormPortletButtonField clearButton;

	private AmiCenterManagerTriggerEditor_AggregateTrigger owner;

	private static final int COLNAME_WIDTH = 150; //60
	private static final int DEFAULT_ROWHEIGHT = 25;
	private static final int DEFAULT_LEFTPOS = 80; //164
	private static final int DEFAULT_SPACING = 10;
	private static final int DEFAULT_TITLEHEIGHT = 27;
	private static final int DEFAULT_TOPPOS = DEFAULT_SPACING + DEFAULT_TITLEHEIGHT;

	public AmiCenterManagerTriggerEditor_AggregateSelectEditor(PortletConfig config, final AmiCenterManagerTriggerEditor_AggregateTrigger owner) {
		super(config);
		this.owner = owner;
		this.selectTitleField = this.addField(new FormPortletTitleField("selects"));
		this.selectTitleField.setLeftPosPx(400);
		this.selectTitleField.setHelp("A comma delimited list of expressions on how to populate target columns from source columns, each expression being of the form:" + "<br>"
				+ "<b><i style=\"color:blue\">targetTableColumn = aggregate_on_sourceTableColumns [,targetTableColumn = aggregate_on_sourceTableColumns ...]</i></b>");

		FormPortletDivField sourceTitleDiv = this.addField(new FormPortletDivField("<b> Source </b>"));
		FormPortletDivField targetTitleDiv = this.addField(new FormPortletDivField("<b> Target </b>"));

		targetTitleDiv.setLeftPosPx(DEFAULT_LEFTPOS + 45).setTopPosPx(DEFAULT_TOPPOS).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(200);
		sourceTitleDiv.setLeftPosPx(DEFAULT_LEFTPOS + COLNAME_WIDTH + DEFAULT_LEFTPOS + 48).setTopPosPx(DEFAULT_TOPPOS).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(200);

		targetColumnField = this.addField(new FormPortletSelectField(String.class, ""));

		aggFuncField = this.addField(new FormPortletSelectField(String.class, "<b>=</b>&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp "));
		//count, countUnique, first, last, max, min, stdev, stdevs, sum
		for (String func : SUPPORTED_FUNC)
			aggFuncField.addOption(func, func);

		targetColumnField.setWidthPx(COLNAME_WIDTH);
		targetColumnField.setHeightPx(DEFAULT_ROWHEIGHT);
		targetColumnField.setLeftPosPx(DEFAULT_LEFTPOS);
		targetColumnField.setTopPosPx(DEFAULT_TOPPOS * 2);
		targetColumnField.setName(AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED);

		aggFuncField.setWidthPx(100);
		aggFuncField.setLeftPosPx(DEFAULT_LEFTPOS + COLNAME_WIDTH + DEFAULT_LEFTPOS);
		aggFuncField.setTopPosPx(DEFAULT_TOPPOS * 2);
		//		sourceColumnField.setRightPosPx(300);
		aggFuncField.setHeightPx(DEFAULT_ROWHEIGHT);
		aggFuncField.setName(AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED);

		aggExpressionField = this.addField(new FormPortletTextField("( &nbsp"));
		aggExpressionField.setHasButton(true);
		aggExpressionField.setName(AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED);
		aggExpressionField.setCorrelationData(new Formula() {

			@Override
			public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
				BasicWebMenu r = new BasicWebMenu();
				AmiWebMenuUtils.createOperatorsMenu(r, AmiWebUtils.getService(getManager()), "");
				//TODO:sub this with real values
				//WebMenu variables = createVariablesMenu("Variables", CH.s("account", "region", "qty", "px"));
				WebMenu variables = createVariablesMenu("Variables", owner.getSourceTableColumns());
				r.add(variables);
				return r;
			}

			@Override
			public void onContextMenu(FormPortletField field, String action) {
				AmiWebMenuUtils.processContextMenuAction(AmiWebUtils.getService(getManager()), action, field);

			}
		});
		aggExpressionField.setLeftPosPx(DEFAULT_LEFTPOS + COLNAME_WIDTH + DEFAULT_LEFTPOS + 140).setTopPosPx(DEFAULT_TOPPOS * 2).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(145);

		this.addButton = this.addField(new FormPortletButtonField(") &nbsp").setValue("Add"));
		this.addButton.setLeftPosPx(DEFAULT_LEFTPOS + COLNAME_WIDTH + DEFAULT_LEFTPOS + 300).setTopPosPx(DEFAULT_TOPPOS * 2).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(80);
		this.addButton.setCssStyle("_fm=bold|_fg=#FFFFFF|_bg=#FFA500|style.borderRadius=5px");
		this.addButton.setName(AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED);

		this.clearButton = this.addField(new FormPortletButtonField("").setValue("Clear"));
		this.clearButton.setLeftPosPx(DEFAULT_LEFTPOS + COLNAME_WIDTH + DEFAULT_LEFTPOS + 390).setTopPosPx(DEFAULT_TOPPOS * 2).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(80);
		this.clearButton.setName(AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED);

		this.outputField = this.addField(new FormPortletTextAreaField("Output"));
		this.outputField.setLeftPosPx(DEFAULT_LEFTPOS).setTopPosPx(DEFAULT_TOPPOS * 2 + DEFAULT_ROWHEIGHT * 2).setHeightPx(DEFAULT_ROWHEIGHT * 5).setWidthPx(600);
		this.outputField.setName("output");

		this.addFormPortletListener(this);
		this.addMenuListener(this);
		this.setMenuFactory(this);

	}

	//add
	public static WebMenu createVariablesMenu(String menuName, Set<String> columns) {
		WebMenu variables = new BasicWebMenu(menuName, true);

		for (String column : CH.sort(columns, SH.COMPARATOR_CASEINSENSITIVE_STRING)) {
			variables.add(new BasicWebMenuLink(column, true, "var_" + column).setAutoclose(false).setCssStyle("_fm=courier"));
		}
		return variables;
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
		this.selectOutput.setLength(0);
		this.selectOutput.append(field.getValue());
	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

	}

	public static interface Formula {
		public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition);
		public void onContextMenu(FormPortletField field, String action);
	}

	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		Formula t = (Formula) field.getCorrelationData();
		return t.createMenu(formPortlet, field, cursorPosition);
	}

	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField field) {
		if (field == aggExpressionField) {
			Formula cb = (Formula) field.getCorrelationData();
			cb.onContextMenu(field, action);
		}
	}

	public void addSelectClause() {
		String targetColumn = this.targetColumnField.getValue();
		String sourceAggFunc = this.aggFuncField.getValue();
		if (this.selectOutput.length() == 0)
			this.selectOutput.append(targetColumn).append(" = ").append(sourceAggFunc).append('(').append(aggExpressionField.getValue()).append(')');
		else
			this.selectOutput.append(',').append(targetColumn).append(" = ").append(sourceAggFunc).append('(').append(aggExpressionField.getValue()).append(')');
		this.outputField.setValue(this.selectOutput.toString());
		owner.getMainEditor().onFieldChanged(outputField);
	}

	public void clearSelectClause() {
		this.selectOutput.setLength(0);
		this.outputField.setValue("");
		owner.getMainEditor().onFieldChanged(outputField);
	}

	public String getOutput() {
		return this.outputField.getValue();
	}

	public FormPortletField<?> getOutputField() {
		return this.outputField;
	}

	public FormPortletField<?> getTargetColumnField() {
		return this.targetColumnField;
	}

	public FormPortletField<?> getAggFuncField() {
		return this.aggFuncField;
	}

	public FormPortletField<?> getAggExpressionField() {
		return this.aggExpressionField;
	}

	public FormPortletButtonField getAddButton() {
		return this.addButton;
	}

	public FormPortletButtonField getClearButton() {
		return this.clearButton;
	}

	public void onTargetTableColumnsChanged() {
		targetColumnField.clearOptions();
		Set<String> targetTableColumns = owner.getTargetTableColumns();
		if (targetTableColumns != null) {
			for (String col : targetTableColumns)
				targetColumnField.addOption(col, col);
		}
		//		targetColumnField.addOption("act", "act");
		//		targetColumnField.addOption("region", "region");
		//		targetColumnField.addOption("cnt", "cnt");
		//		targetColumnField.addOption("value", "value");
	}
}
