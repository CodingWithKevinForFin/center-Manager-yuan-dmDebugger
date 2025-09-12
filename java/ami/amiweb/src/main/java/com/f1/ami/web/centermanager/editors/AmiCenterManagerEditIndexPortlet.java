package com.f1.ami.web.centermanager.editors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.f1.ami.amicommon.AmiUtils;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.portlets.AmiCenterManagerReviewApplyScriptPortlet;
import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.portal.Portlet;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletMultiCheckboxField;
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.utils.CH;
import com.f1.utils.SH;
import com.f1.utils.casters.Caster_String;
import com.f1.utils.string.ExpressionParserException;
import com.f1.utils.string.JavaExpressionParser;
import com.f1.utils.string.Node;
import com.f1.utils.string.node.ConstNode;
import com.f1.utils.string.node.DeclarationNode;
import com.f1.utils.string.node.MethodNode;
import com.f1.utils.string.sqlnode.AdminNode;
import com.f1.utils.string.sqlnode.SqlOperationNode;
import com.f1.utils.string.sqlnode.UseNode;
import com.f1.utils.structs.Tuple2;
import com.f1.utils.structs.table.SmartTable;

public class AmiCenterManagerEditIndexPortlet extends AmiCenterManagerAbstractEditCenterObjectPortlet implements AmiCenterManagerOptionFieldListener {
	
	final private FormPortletTextField nameField;
	final private FormPortletMultiCheckboxField<String> onField;
	final private FormPortletSelectField<Short> constraintField;
	final private FormPortletSelectField<Short> autogenField;
	
	/*strcture:
	 * <GridPortlet*formGrid:
	 *  1. FormPortlet*form1: nameField,onField<br>
	 *  2. IndexConfigForm*form2 indexConfigForm<br>
	 *  3. FormPortlet*form3: constraintField,autogenField<br>
	 * */
	//form
	final private GridPortlet gridForm;
	final private FormPortlet form1;
	final private AmiCenterManagerIndexConfigForm form2;
	final private FormPortlet form3;
	private int curIndexSize = -1;
	private List<String> columns = new ArrayList<String>();
	private List<Tuple2<String, String>> origIndexConfig = new ArrayList<Tuple2<String, String>> ();
	
	private String sql;
	
	public AmiCenterManagerEditIndexPortlet(PortletConfig config, boolean isAdd) {
		super(config, isAdd);
		
		this.gridForm = new GridPortlet(generateConfig());
		
		this.form1 = new FormPortlet(generateConfig());
		this.form1.setMenuFactory(this);
		this.form1.addMenuListener(this);
		this.form1.addFormPortletListener(this);

		this.form3 = new FormPortlet(generateConfig());
		this.form3.setMenuFactory(this);
		this.form3.addMenuListener(this);
		this.form3.addFormPortletListener(this);

		//fields
		this.nameField = new FormPortletTextField(AmiCenterEntityConsts.OPTION_NAME_INDEX_NAME + AmiCenterEntityConsts.REQUIRED_FIELD_ANNOTATION_HTML);
		this.nameField.setName(AmiCenterEntityConsts.OPTION_NAME_INDEX_NAME);
		this.nameField.setHelp("Name of the index, each index's name must be unique for the table");
		this.nameField.setTopPosPx(30).setLeftPosPx(125).setWidth(600).setHeight(25);

		this.onField = new FormPortletMultiCheckboxField(String.class, AmiCenterEntityConsts.OPTION_NAME_INDEX_ON + AmiCenterEntityConsts.REQUIRED_FIELD_ANNOTATION_HTML);
		this.onField.setTopPosPx(70).setLeftPosPx(125).setWidth(600).setHeight(25);
		for (String k : CH.sort(service.getSystemObjectsManager().getTableNames())) {
			if (!SYSTEM_TABLES.contains(k))
				onField.addOption(k, k);
		}
		this.onField.setName(AmiCenterEntityConsts.OPTION_NAME_INDEX_ON);
		this.onField.setHelp("Name of the table to add the index to");
		

		this.constraintField = new FormPortletSelectField(short.class, "CONSTRAINT");
		this.constraintField.setId(AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT);
		this.constraintField.setName(AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT);
		this.constraintField.addOption(AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_CODE_NONE, AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_NONE);
		this.constraintField.addOption(AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_CODE_UNIQUE, AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_UNIQUE);
		this.constraintField.addOption(AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_CODE_PRIMARY, AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_PRIMARY);
		this.constraintField.setHelp("Constraints can be added to an index to determine the outcome of a key collision. Three different types of constraints are supported:<br>"
				+ "<ul><li>(1). <b style=\"color:blue\"><i>NONE</i></b> (default): If a constraint is not supplied, this is the default. There is no restriction on having multiple rows with the same key.<br></li>"
				+ "<li>(2). <b style=\"color:blue\"><i>UNIQUE</i></b>: An attempt to insert (or update) a row such that two rows in the table will have the same key will fail.<br></li>"
				+ "<li>(3). <b style=\"color:blue\"><i>PRIMARY</i></b>: An attempt to insert a row with the same key as an existing row will cause the existing row to be updated instead of a new row being inserted (specifically, those cells specified and not participating in the index will be updated). This can be thought of as an \"UPSERT\" in other popular databases. An attempt to update a row such that two rows in the table will have the same key will fail. Each table can have at most one PRIMARY index.</li></ul>");
		autogenField = new FormPortletSelectField(short.class, "AUTOGEN");
		autogenField.setId(AmiCenterEntityConsts.OPTION_NAME_INDEX_AUTOGEN);
		autogenField.setName(AmiCenterEntityConsts.OPTION_NAME_INDEX_AUTOGEN);
		autogenField.addOption(AmiCenterEntityConsts.AUTOGEN_TYPE_CODE_NONE, AmiCenterEntityConsts.AUTOGEN_TYPE_NONE);
		autogenField.addOption(AmiCenterEntityConsts.AUTOGEN_TYPE_CODE_RAND, AmiCenterEntityConsts.AUTOGEN_TYPE_RAND);
		autogenField.addOption(AmiCenterEntityConsts.AUTOGEN_TYPE_CODE_INC, AmiCenterEntityConsts.AUTOGEN_TYPE_INC);
		autogenField.setHelp("Primary indexes can also be automatically generated on a particular column using AUTOGEN, where two options are available:<br>"
				+ "<ul><li>(1). <b style=\"color:blue\"><i>RAND</i></b>:A random UID is assigned to the column with a unique value for each row.<br></li>"
				+ "<li>(2). <b style=\"color:blue\"><i>INC</i></b>:An auto-incrementing UID is assigned to the column with a unique value for each row, starting at 0 for the first row, 1 for the second row and etc. Note that this option is only supported for <b><i>INT</i></b> and <b><i>LONG</i></b> columns.</li></ul>");
		form1.addField(nameField);
		form1.addField(onField);
		form3.addField(constraintField);
		form3.addField(autogenField);
		autogenField.setVisible(false);
		
		this.gridForm.addChild(form1, 0, 0);
		this.form2 = this.gridForm.addChild(new AmiCenterManagerIndexConfigForm(this, generateConfig()), 0, 1);
		this.gridForm.addChild(form3, 0, 2);
		
	
		//add menu factory listener to form2
		this.form2.addMenuListener(this);
		this.form2.setMenuFactory(this);
		this.form2.addFormPortletListener(this);
		addChild(form1, 0, 0);
		addChild(form2, 0, 1);
		addChild(form3, 0, 2);
		addChild(buttonsFp, 0, 3);
		if(isAdd) {
			//add an empty index for the user to configure
			curIndexSize = 1;
			this.form2.addIndexFieldAtPos(0);
		}	
		setRowSize(0, 150);
		setRowSize(1, 280);
		

		setRowSize(3, buttonsFp.getButtonPanelHeight());

	}
	
	public AmiCenterManagerEditIndexPortlet(PortletConfig config, String sql) {
		this(config, false);
		this.sql = sql;
		importFromText(sql, new StringBuilder());
		enableEdit(false);
	}

	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		if (formPortlet != form2)
			return null;
		if (field.getName().equals(AmiCenterManagerIndexConfigForm.TYPE_INDEXTYPE)) {
			BasicWebMenu r = new BasicWebMenu();

			this.form2.createIndexFieldsContextMenu(field, r);
			return r;
		}
		if (field.getName().equals(AmiCenterManagerIndexConfigForm.TYPE_COLNAME)) {
			BasicWebMenu r = new BasicWebMenu();
			
			this.form2.createColNameFieldsContextMenu(field, r, columns);
			return r;
		}
		return null;
	}
	
	
	//TODO:
	public boolean hasEdit() {
		return !this.editedFields.isEmpty() || !form2.getCurIndexConfig().equals(origIndexConfig);
	}
	
	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField node) {		
		if (portlet == this.form2) {
			this.form2.onIndexFieldsFormContextMenu(action, node);
		}
	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void checkCanDropAndRecreate() {
		
		
	}

	@Override
	public String previewEdit() {
		String on = SH.is(getOnValue()) ? AmiUtils.escapeVarName(getOnValue()) : "";
		String sql =  "DROP INDEX " + AmiUtils.escapeVarName(nameField.getDefaultValue()) + " ON " + on + ";" + previewScript();
		return sql;
	}
	
	public String getOnValue() {
		LinkedHashSet<String> ons = onField.getValue();
		if(ons.size() == 1)
			return (String)ons.toArray()[0];
		AmiCenterManagerUtils.popDialog(service, "The index can only bind to exactly 1 table", "Error Editing Index");
		return null;
	}
	
	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		super.onFieldValueChanged(portlet, field, attributes);
		onFieldChanged(field);
		if(field == onField && !onField.getValue().isEmpty()) {
			sendQueryToBackend("SHOW COLUMNS WHERE TableName == \"" + AmiUtils.escapeVarName(getOnValue()) + "\" ORDER BY Position;");	
		} else if(field == constraintField) {
			showAutoGen();
		}
		if(hasEdit())
			applyButton.setEnabled(true);
	}
	
	public boolean canShowAutogen() {
		//first check the number of indexes is exactly 1 
		if(form2.getSize() > 0 && form2.getSize() != 1)
			return false;
		
		//then check that the constraint is primary
		if(constraintField.getValue() != AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_CODE_PRIMARY)
			return false;
		return true;
	}
	
	
	@Override
	public String prepareUseClause() {		
		StringBuilder script = new StringBuilder();
		if (this.constraintField.getValue() != AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_CODE_NONE)
			script.append(AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT).append(" = ")
					.append(SH.doubleQuote(AmiCenterManagerUtils.toIndexConstraint(this.constraintField.getValue()))).append(" ");
		if (this.autogenField.getValue() != AmiCenterEntityConsts.AUTOGEN_TYPE_CODE_NONE)
			script.append(AmiCenterEntityConsts.OPTION_NAME_INDEX_AUTOGEN).append(" = ").append(SH.doubleQuote(AmiCenterManagerUtils.toIndexAutogen(this.autogenField.getValue())));
		return script.toString();
		
		
	}

	@Override
	public String preparePreUseClause() {
		StringBuilder sb = new StringBuilder("CREATE INDEX ");
		if(SH.is(nameField.getValue()))
			sb.append(AmiUtils.escapeVarName(nameField.getValue()));
		if(!onField.getValue().isEmpty())
			sb.append(" ON ").append(AmiUtils.escapeVarName(getOnValue()));
		sb.append('(');
		for (int i = 0; i < this.form2.getSize(); i++) {
			if(SH.is(form2.getIndexColumnNameAt(i).getValue()))
				sb.append(AmiUtils.escapeVarName(form2.getIndexColumnNameAt(i).getValue())).append(' ');
			sb.append(AmiCenterManagerUtils.toIndexType((short) form2.getIndexTypeAt(i).getValue()));
			if (i != this.form2.getSize() - 1)
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
		if(text == null)
			return;
		String idxName, tableName = null;
		Map<String, Node> useOptions = null;
		origIndexConfig.clear();
		form2.resetIndexFields();
		try {
			AdminNode an = AmiCenterManagerUtils.scriptToAdminNode(text);
			SqlOperationNode indexNode =  JavaExpressionParser.castNode(an.getNext(), SqlOperationNode.class);
			MethodNode methodNode = JavaExpressionParser.castNode(indexNode.getNext(), MethodNode.class);
			UseNode useNode = an.getUseNode();
			idxName = indexNode.getNameAsString();
			tableName = methodNode.getMethodName();
			nameField.setValue(idxName);
			nameField.setDefaultValue(idxName);
			onField.setValue(Collections.singleton(tableName));
			onField.setDefaultValue(Collections.singleton(tableName));
			
			//need to parse autogen and primary first to determine if we can make autogen visible
			if(useNode != null) {
				useOptions = useNode.getOptionsMap();
				for (Entry<String, Node> s : useOptions.entrySet()) {
					String key = s.getKey();
					ConstNode sval = (ConstNode)s.getValue();
					if ("constraint".equalsIgnoreCase(key)) {
						String t = (String) sval.getValue();
						constraintField.setValue(AmiCenterManagerUtils.toIndexConstraintCode(t));
						constraintField.setDefaultValue(AmiCenterManagerUtils.toIndexConstraintCode(t));
					}else if("autogen".equalsIgnoreCase(key)) {
						String t =  (String) sval.getValue();
						autogenField.setValue(AmiCenterManagerUtils.toIndexAutogenCode(t));
						autogenField.setDefaultValue(AmiCenterManagerUtils.toIndexAutogenCode(t));
						autogenField.setVisible(true);
					}
				}
			}
			for(int i = 0; i < methodNode.getParamsCount(); i++) {
				DeclarationNode n = (DeclarationNode) methodNode.getParamAt(i);
				String col = n.getVartype();
				String idxType = n.getVarname();
				form2.addIndexFieldAtPos(i);
				origIndexConfig.add(new Tuple2<String, String>(col, idxType));
				
				FormPortletTextField colNameField = this.form2.getIndexColumnNameAt(i);
				FormPortletSelectField indexTypeField = this.form2.getIndexTypeAt(i);
				colNameField.setValue(col);
				indexTypeField.setValue(AmiCenterManagerUtils.toIndexTypeCode(idxType));
			}
			
			
		
		}catch(Exception e) {
			AmiCenterManagerUtils.popDialog(service, e.getMessage(), "Error importing Script");
		}
	}
	
	private void showAutoGen() {
		if(canShowAutogen())
			autogenField.setVisible(true);
		else {
			autogenField.setVisible(false);
			//when the constraint is not primary, autogen should always be reset to NONE
			autogenField.setValue(AmiCenterEntityConsts.AUTOGEN_TYPE_CODE_NONE);
			onFieldChanged(this.autogenField);
		}
			

	}

	@Override
	public void enableEdit(boolean enable) {
		// TODO Auto-generated method stub
		
	}
	
	public int getIndexCount() {
		return this.form2.getSize();
	}
	
	private boolean isAllIndexFieldFilled() {
		for (int i = 0; i < this.getIndexCount(); i++) {
			FormPortletTextField colNameFld = this.form2.getIndexColumnNameAt(i);
			if (SH.isnt(colNameFld.getValue())) {
				AmiCenterManagerUtils.popDialog(service, "Index at location " + i + " missing a name", "Warning");
				return false;

			}
		}
		return true;
	}
	

	@Override
	public boolean ensureCanProceedWithApply() {
		//check if all the required fields have been filled in 
		if(SH.isnt(getOnValue()))
			return false;
		if(SH.isnt(nameField.getValue())) {
			AmiCenterManagerUtils.popDialog(service, "Missing required field: Name", "Warning");
			return false;
		}
		
		//check the indexes are configured
		if (!isAllIndexFieldFilled()) {
			return false;
		}
		if(!hasEdit() && !isAdd) {
			AmiCenterManagerUtils.popDialog(service, "No changes detected", "Warning");
			return false;
		}
		//check autogen
		if (this.autogenField.getValue() != AmiCenterEntityConsts.AUTOGEN_TYPE_CODE_NONE && getIndexCount() != 1) {
			AmiCenterManagerUtils.popDialog(service, "Autogen can only apply to ONE column with primary constraint", "Warning");
			return false;
		}
		
		//check only the first 64 columns can participate in an index:	
		for(Tuple2<String, String> idx: form2.getCurIndexConfig()) {
			String colname = idx.getA();
			int position = columns.indexOf(colname);
			if(position == -1) {
				//throw new NullPointerException("Column:" + colname + " does not exist");
				AmiCenterManagerUtils.popDialog(service, "Column:" + colname + " does not exist", "Error Editing Index");
				return false;
			}
				
			if(position > 63) {
				AmiCenterManagerUtils.popDialog(service, "only the first 64 columns can participate in an index", "Error Editing Index");
				return false;
			}
		}
		
		//check if the variables/columns are valid
		return true;
	}
	
	
	private void updateSuggestedSizeOfWhereFieldForm() {
		this.gridForm.setRowSize(1, form2.getSuggestedHeight(null));
	}
	
	@Override
	public void onOptionFieldAdded() {
		updateSuggestedSizeOfWhereFieldForm();
		showAutoGen();
		//check if there are changes on the index config
		if(!isAdd) {
			if (form2.getSize() != this.curIndexSize) {
				this.applyButton.setEnabled(true);
				this.resetButton.setEnabled(true);
			} else {
				this.applyButton.setEnabled(false);
				this.resetButton.setEnabled(false);
			}		
		}
		
	}
	
	@Override
	public void revertEdit() {
		importFromText(sql, new StringBuilder());
	}

	@Override
	public void onOptionFieldRemoved() {
		updateSuggestedSizeOfWhereFieldForm();
		showAutoGen();
		if(!isAdd) {
			if (form2.getSize() != this.curIndexSize) {
				this.applyButton.setEnabled(true);
				this.resetButton.setEnabled(true);
			}		
		}
	
	}

	@Override
	public void onOptionFieldEdited(FormPortletField<?> field) {
		onFieldChanged(field);
		
	}
	
	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		Action a = result.getRequestMessage().getAction();
		String query = null;
		if (a instanceof AmiCenterQueryDsRequest) {
			AmiCenterQueryDsRequest request = (AmiCenterQueryDsRequest) a;
			query = request.getQuery();
		}
		if (response.getOk() && response.getTables().size() == 1) {
			Table t = response.getTables().get(0);
			if(query.startsWith("SHOW FULL COLUMNS")) {
				columns.clear();
				for(Row r: t.getRows()) {
					String colName = (String) r.get("ColumnName");
					columns.add(colName);
				}
				
			}else if(query.startsWith("DESCRIBE INDEX")) {
				String sql = (String) t.getRow(0).get("SQL");
				importFromText(sql,  new StringBuilder());
			}
		}
	}
	

}
