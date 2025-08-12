package com.f1.ami.web.centermanager.editors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebAmiScriptCallback;
import com.f1.ami.web.AmiWebCompilerListener;
import com.f1.ami.web.AmiWebConsts;
import com.f1.ami.web.AmiWebDomObject;
import com.f1.ami.web.AmiWebDomObjectDependency;
import com.f1.ami.web.AmiWebFormula;
import com.f1.ami.web.AmiWebLayoutHelper;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Index;
import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.fastwebcolumns.FastWebColumns;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.portal.Portlet;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.PortletManager;
import com.f1.suite.web.portal.impl.ConfirmDialogPortlet;
import com.f1.suite.web.portal.impl.DividerPortlet;
import com.f1.suite.web.portal.impl.FastTreePortlet;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.TabPortlet;
import com.f1.suite.web.portal.impl.TreeStateCopierIdGetter;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuFactory;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuListener;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.suite.web.portal.style.PortletStyleManager_Dialog;
import com.f1.suite.web.tree.WebTreeContextMenuListener;
import com.f1.suite.web.tree.WebTreeNode;
import com.f1.suite.web.tree.impl.FastWebTree;
import com.f1.suite.web.tree.impl.FastWebTreeColumn;
import com.f1.utils.OH;
import com.f1.utils.SH;
import com.f1.utils.casters.Caster_String;
import com.f1.utils.concurrent.IdentityHashSet;
import com.f1.utils.structs.LongKeyMap;
import com.f1.utils.structs.Tuple2;
import com.f1.utils.structs.table.SmartTable;
import com.f1.utils.structs.table.derived.DerivedCellCalculator;

public class AmiCenterManagerIndexScirptTreePortlet extends GridPortlet implements Comparator<WebTreeNode>, WebTreeContextMenuListener, FormPortletContextMenuFactory,
		FormPortletListener, FormPortletContextMenuListener, AmiWebCompilerListener, TreeStateCopierIdGetter, AmiWebDomObjectDependency, AmiCenterManagerOptionFieldListener {
	//create index myindex on accounts(id hash) use constraint="";
	public static final String DEFAULT_DS_NAME = "AMI";
	public static final byte DEFAULT_PERMISSION = (byte) 15;
	//Backend config
	public static final int DEFAULT_LIMIT = 10000;
	public static final int DEFAULT_TIMEOUT = 60000;
	private long sessionId = -1;

	private LongKeyMap<List<WebTreeNode>> nodesByGraphId = new LongKeyMap<List<WebTreeNode>>();

	final private DividerPortlet divider;
	final private FastTreePortlet tree;
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

	//common fields
	private FormPortletTextField nameField;
	private FormPortletTextField onField;
	private FormPortletTextField indexField;
	private FormPortletSelectField<Short> constraintField;
	private FormPortletSelectField<Short> autogenField;

	final private AmiWebService service;
	//buttons
	final private FormPortletButton testButton;
	final private FormPortletButton resetButton;
	final private FormPortletButton diffButton;
	final private FormPortletButton previewButton;

	private Map<String, String> fieldCache = new HashMap<String, String>();
	private List<Tuple2<String, String>> indexCache = new ArrayList<Tuple2<String, String>>();

	//In the format of [col1 HASH, col2 SORT...]
	private List<String> curIndexConfig = new ArrayList<String>();
	private int curIndexSize = -1;
	private TabPortlet owningTab;

	//edited fields
	final private IdentityHashSet<FormPortletTextField> editedFields = new IdentityHashSet<FormPortletTextField>();
	final private IdentityHashSet<FormPortletSelectField> editedSelectFields = new IdentityHashSet<FormPortletSelectField>();

	private WebTreeNode treeNodeIndexes;

	public AmiCenterManagerIndexScirptTreePortlet(PortletConfig config, Map<String, AmiCenterGraphNode_Index> indexBinding, TabPortlet owningTab) {
		super(config);
		this.service = AmiWebUtils.getService(getManager());
		this.owningTab = owningTab;
		this.divider = new DividerPortlet(generateConfig(), true);
		this.divider.setOffsetFromTopPx(300);
		this.addChild(divider);

		this.tree = new FastTreePortlet(generateConfig());
		this.tree.getTree().setComparator(this);
		this.tree.getTree().addMenuContextListener(this);
		//add default form and dialog style for this.tree
		this.tree.setFormStyle(AmiWebUtils.getService(getManager()).getUserFormStyleManager());
		this.tree.setDialogStyle(AmiWebUtils.getService(getManager()).getUserDialogStyleManager());
		this.tree.getTree().setRootLevelVisible(false);
		buildTree(indexBinding);

		//form
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

		this.onField = new FormPortletTextField(AmiCenterEntityConsts.OPTION_NAME_INDEX_ON + AmiCenterEntityConsts.REQUIRED_FIELD_ANNOTATION_HTML);
		this.onField.setName(AmiCenterEntityConsts.OPTION_NAME_INDEX_ON);
		this.onField.setHelp("Name of the table to add the index to");
		this.onField.setDisabled(true);//disallow editing on field

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
		//buttons
		this.testButton = new FormPortletButton("Test");
		this.resetButton = new FormPortletButton("Reset");
		this.diffButton = new FormPortletButton("Diff");
		this.previewButton = new FormPortletButton("Preview");
		this.testButton.setEnabled(false);
		this.resetButton.setEnabled(false);
		this.diffButton.setEnabled(false);
		this.previewButton.setEnabled(false);

		//add child to the gridform
		this.gridForm.addChild(form1, 0, 0);
		this.form2 = this.gridForm.addChild(new AmiCenterManagerIndexConfigForm(this, generateConfig()), 0, 1);
		this.gridForm.addChild(form3, 0, 2);

		//add menu factory listener to form2
		this.form2.addMenuListener(this);
		this.form2.setMenuFactory(this);
		this.form2.addFormPortletListener(this);
		//this.form2.initIndexField();

		//allocate 70 px for form1
		this.gridForm.setRowSize(0, 70);

		form3.addButton(testButton);
		form3.addButton(resetButton);
		form3.addButton(diffButton);
		form3.addButton(previewButton);

		this.divider.addChild(this.tree);
		this.divider.addChild(this.gridForm);
		this.service.addCompilerListener(this);
		this.service.getDomObjectsManager().addGlobalListener(this);

		sendAuth();
	}

	private void revertIndexChanges() {
		//reset index config
		this.form2.resetIndexFields();
		for (int i = 0; i < this.curIndexSize; i++) {
			this.form2.addIndexFieldAtPos(i);

			Tuple2<String, String> pair = indexCache.get(i);
			String origColName = pair.getA();
			String origIndexType = pair.getB();

			FormPortletTextField colNameFld = this.form2.getIndexColumnNameAt(i);
			colNameFld.setValue(origColName);
			FormPortletSelectField<Short> indexTypeFld = this.form2.getIndexTypeAt(i);
			indexTypeFld.setValue(AmiCenterManagerUtils.toIndexTypeCode(origIndexType));
		}

	}

	private void buildTree(Map<String, AmiCenterGraphNode_Index> indexBinding) {
		this.tree.clear();
		this.nodesByGraphId.clear();
		this.treeNodeIndexes = createNode(this.tree.getRoot(), "Indexes", AmiWebConsts.CENTER_GRAPH_NODE_INDEX, null);
		for (Entry<String, AmiCenterGraphNode_Index> e : indexBinding.entrySet()) {
			String triggerName = e.getKey();
			AmiCenterGraphNode_Index index = e.getValue();
			createNode(this.treeNodeIndexes, index);
		}
	}

	private WebTreeNode createNode(WebTreeNode parent, String title, String icon, Object data) {
		WebTreeNode r = this.tree.createNode(title, parent, false, data);
		r.setIconCssStyle(icon == null ? null : "_bgi=url('" + icon + "')");
		return r;
	}

	private WebTreeNode createNode(WebTreeNode parent, AmiCenterGraphNode node) {
		String icon = getIcon(node);
		String label = node.getLabel();
		WebTreeNode r = parent.getTreeManager().createNode(label, parent, false, node);
		r.setIconCssStyle(icon == null ? null : "_bgi=url('" + icon + "')");
		LongKeyMap.Node<List<WebTreeNode>> entry = this.nodesByGraphId.getNodeOrCreate(node.getUid());
		if (entry.getValue() == null)
			entry.setValue(new ArrayList<WebTreeNode>());
		entry.getValue().add(r);

		return r;
	}

	public static String getIcon(AmiCenterGraphNode node) {
		switch (node.getType()) {
			case AmiCenterGraphNode.TYPE_TABLE:
				return AmiWebConsts.CENTER_GRAPH_NODE_TABLE;
			case AmiCenterGraphNode.TYPE_TRIGGER:
				return AmiWebConsts.CENTER_GRAPH_NODE_TRIGGER;
			case AmiCenterGraphNode.TYPE_TIMER:
				return AmiWebConsts.CENTER_GRAPH_NODE_TIMER;
			case AmiCenterGraphNode.TYPE_PROCEDURE:
				return AmiWebConsts.CENTER_GRAPH_NODE_PROCEDURE;
			case AmiCenterGraphNode.TYPE_INDEX:
				return AmiWebConsts.CENTER_GRAPH_NODE_INDEX;
			case AmiCenterGraphNode.TYPE_DBO:
				return AmiWebConsts.CENTER_GRAPH_NODE_DBO;
			case AmiCenterGraphNode.TYPE_METHOD:
				return AmiWebConsts.CENTER_GRAPH_NODE_METHOD;
		}
		return null;
	}

	@Override
	public void onClosed() {
		this.service.removeCompilerListener(this);
		this.service.getDomObjectsManager().removeGlobalListener(this);
		super.onClosed();
	}

	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		if (sessionId == -1) {
			this.sessionId = response.getQuerySessionId();
		}
		if (response.getOk() && response.getTables() != null && response.getTables().size() == 1) {
			List<Table> tables = response.getTables();
			Table t = tables.get(0);
			String indexName = (String) t.getRow(0).get("IndexName");
			String indexOn = (String) t.getRow(0).get("TableName");
			String indexAutogen = (String) t.getRow(0).get("AutoGen");
			String indexConstraint = (String) t.getRow(0).get("Constraint");

			int size = t.getRows().size();
			this.curIndexSize = size;
			//clear old fields
			this.form2.resetIndexFields();
			this.curIndexConfig.clear();
			for (int i = 0; i < size; i++) {
				Row r = t.getRow(i);
				String columnName = (String) r.get("ColumnName");
				String indexType = (String) r.get("IndexType");

				//first generate the field and then populate
				this.form2.addIndexFieldAtPos(i);
				FormPortletTextField colNameField = this.form2.getIndexColumnNameAt(i);
				FormPortletSelectField indexTypeField = this.form2.getIndexTypeAt(i);
				colNameField.setValue(columnName);
				indexTypeField.setValue(AmiCenterManagerUtils.toIndexTypeCode(indexType));
				Tuple2<String, String> pair = new Tuple2<String, String>();
				pair.setA(columnName);
				pair.setB(indexType);
				this.indexCache.add(pair);
				this.curIndexConfig.add(columnName + " " + indexType);
			}

			//set field value
			//1.Name
			this.nameField.setValue(indexName);
			this.fieldCache.put(AmiCenterEntityConsts.OPTION_NAME_INDEX_NAME, indexName);

			//2.on
			this.onField.setValue(indexOn);
			this.fieldCache.put(AmiCenterEntityConsts.OPTION_NAME_INDEX_ON, indexOn);

			//3.constraint
			this.constraintField.setValue(AmiCenterManagerUtils.toIndexConstraintCode(indexConstraint));
			this.fieldCache.put(AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT, indexConstraint);
			if (AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_PRIMARY.equals(indexConstraint))
				this.autogenField.setVisible(true);

			//4.autogen
			this.autogenField.setValue(AmiCenterManagerUtils.toIndexAutogenCode(indexAutogen));
			this.fieldCache.put(AmiCenterEntityConsts.OPTION_NAME_INDEX_AUTOGEN, indexAutogen);
		}
	}

	private AmiCenterQueryDsRequest prepareRequest() {
		AmiCenterQueryDsRequest request = getManager().getTools().nw(AmiCenterQueryDsRequest.class);

		int timeout = 60;
		try {
			timeout = (int) (timeout * 1000);
		} catch (Exception e) {
			getManager().showAlert("Timeout is not in a valid format");
			return null;
		}

		request.setTimeoutMs(timeout);
		request.setQuerySessionKeepAlive(true);
		request.setInvokedBy(service.getUserName());
		request.setSessionVariableTypes(null);
		request.setSessionVariables(null);
		request.setAllowSqlInjection(false);//String template, dflt to false
		request.setPermissions(DEFAULT_PERMISSION);
		request.setType(AmiCenterQueryDsRequest.TYPE_QUERY);
		request.setOriginType(AmiCenterQueryDsRequest.ORIGIN_FRONTEND_SHELL);
		request.setDatasourceName(DEFAULT_DS_NAME);
		request.setLimit(-1);
		return request;
	}

	private void prepareRequestToBackend(String query) {
		AmiCenterQueryDsRequest request = prepareRequest();
		request.setQuery(query);
		request.setQuerySessionId(this.sessionId);
		service.sendRequestToBackend(this, request);
	}

	private void sendAuth() {
		AmiCenterQueryDsRequest request = prepareRequest();
		if (request == null)
			return;
		service.sendRequestToBackend(this, request);
	}

	@Override
	public void onUserDblclick(FastWebColumns columns, String action, Map<String, String> properties) {
		// TODO Auto-generated method stub

	}
	@Override
	public void initLinkedVariables() {
		// TODO Auto-generated method stub

	}
	@Override
	public void onDomObjectAriChanged(AmiWebDomObject target, String oldAri) {
		// TODO Auto-generated method stub

	}
	@Override
	public void onDomObjectEvent(AmiWebDomObject object, byte eventType) {
		// TODO Auto-generated method stub

	}
	@Override
	public void onDomObjectRemoved(AmiWebDomObject object) {
		// TODO Auto-generated method stub

	}
	@Override
	public void onDomObjectAdded(AmiWebDomObject object) {
		// TODO Auto-generated method stub

	}
	@Override
	public Object getId(WebTreeNode node) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void onFormulaChanged(AmiWebFormula formula, DerivedCellCalculator old, DerivedCellCalculator nuw) {
		// TODO Auto-generated method stub

	}
	@Override
	public void onCallbackChanged(AmiWebAmiScriptCallback callback, DerivedCellCalculator old, DerivedCellCalculator nuw) {
		// TODO Auto-generated method stub

	}
	@Override
	public void onRecompiled() {
		// TODO Auto-generated method stub

	}
	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField node) {
		if (portlet == this.form2) {
			this.form2.onIndexFieldsFormContextMenu(action, node);
		}

	}
	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		if (this.resetButton == button) {
			for (FormPortletTextField f : this.editedFields) {
				String orig = this.fieldCache.get(f.getName());
				f.setValue(orig);
				onFieldChanged(f);
			}
			//check constraint and autogen
			for (FormPortletSelectField sf : this.editedSelectFields) {
				if (AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT.equals(sf.getName())) {
					String orig = this.fieldCache.get(sf.getName());
					short origTypeCode = AmiCenterManagerUtils.toIndexConstraintCode(orig);
					sf.setValue(origTypeCode);
					onFieldChanged(sf);
				} else if (AmiCenterEntityConsts.OPTION_NAME_INDEX_AUTOGEN.equals(sf.getName())) {
					String orig = this.fieldCache.get(sf.getName());
					short origTypeCode = AmiCenterManagerUtils.toIndexAutogenCode(orig);
					sf.setValue(origTypeCode);
					onFieldChanged(sf);
				}
			}
			//lastly revert index changes if any
			this.revertIndexChanges();

		} else if (this.previewButton == button) {
			String text = AmiCenterManagerUtils.formatPreviewScript(previewScript());
			PortletStyleManager_Dialog dp = service.getPortletManager().getStyleManager().getDialogStyle();
			final PortletManager portletManager = service.getPortletManager();
			ConfirmDialogPortlet cdp = new ConfirmDialogPortlet(portletManager.generateConfig(), text, ConfirmDialogPortlet.TYPE_MESSAGE);
			int w = dp.getDialogWidth();
			int h = dp.getDialogHeight();
			portletManager.showDialog("Index Script", cdp, w + 200, h);
		} else if (this.diffButton == button) {
			LinkedHashMap a = new LinkedHashMap<String, Map>();
			LinkedHashMap b = new LinkedHashMap<String, Map>();
			//first diff name
			if (!this.editedFields.isEmpty()) {
				a.put(AmiCenterEntityConsts.OPTION_NAME_INDEX_NAME, fieldCache.get(AmiCenterEntityConsts.OPTION_NAME_INDEX_NAME));
				b.put(AmiCenterEntityConsts.OPTION_NAME_INDEX_NAME, this.nameField.getValue());
			}

			//then diff index configuration
			Tuple2<Map<Integer, String>, Map<Integer, String>> diffs = getIndexDiff();
			a.put("Index Configuration", diffs.getA());
			b.put("Index Configuration", diffs.getB());

			//lastly diff the constarint and autogen
			if (!this.editedSelectFields.isEmpty()) {
				for (FormPortletSelectField sf : this.editedSelectFields) {
					if (AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT.equals(sf.getName())) {
						a.put(AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT, fieldCache.get(AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT));
						b.put(AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT, AmiCenterManagerUtils.toIndexConstraint(this.constraintField.getValue()));
					} else if (AmiCenterEntityConsts.OPTION_NAME_INDEX_AUTOGEN.equals(sf.getName())) {
						a.put(AmiCenterEntityConsts.OPTION_NAME_INDEX_AUTOGEN, fieldCache.get(AmiCenterEntityConsts.OPTION_NAME_INDEX_AUTOGEN));
						b.put(AmiCenterEntityConsts.OPTION_NAME_INDEX_AUTOGEN, AmiCenterManagerUtils.toIndexAutogen(this.autogenField.getValue()));
					}
				}
			}
			String oldConfig = AmiWebLayoutHelper.toJson(a, service);
			String newConfig = AmiWebLayoutHelper.toJson(b, service);
			AmiWebUtils.diffConfigurations(service, oldConfig, newConfig, "Orginal Script", "New Script", null);
		} else if (this.testButton == button) {
			if (!validateFields())
				return;
			String tableName = this.onField.getValue();
			String query = "DROP INDEX " + this.fieldCache.get(AmiCenterEntityConsts.OPTION_NAME_INDEX_NAME) + " ON " + tableName + ";" + previewScript();
			getManager().showDialog("Submit Index", new AmiCenterManagerSubmitEditScriptPortlet(this.service, generateConfig(), query),
					AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PORTLET_WIDTH, AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PORTLET_HEIGHT);
		}
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

	private boolean validateFields() {
		//check if all the required fields have been filled in 
		if (!isAllIndexFieldFilled()) {
			return false;
		}

		//check autogen
		if (this.autogenField.getValue() != AmiCenterEntityConsts.AUTOGEN_TYPE_CODE_NONE && getIndexCount() != 1) {
			AmiCenterManagerUtils.popDialog(service, "Autogen can only apply to ONE column with primary constraint", "Warning");
			return false;
		}

		return true;
	}

	public int getIndexCount() {
		return this.form2.getSize();
	}

	private boolean hasIndexConfigChanged() {
		Tuple2<Map<Integer, String>, Map<Integer, String>> indexDiff = getIndexDiff();
		Map<Integer, String> oldConfig = indexDiff.getA();
		Map<Integer, String> nuwConfig = indexDiff.getB();
		return oldConfig.equals(nuwConfig) ? true : false;
	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		onFieldChanged(field);
	}

	private Tuple2<Map<Integer, String>, Map<Integer, String>> getIndexDiff() {
		Tuple2<Map<Integer, String>, Map<Integer, String>> diffPair = new Tuple2<Map<Integer, String>, Map<Integer, String>>();
		LinkedHashMap<Integer, String> newConfig = new LinkedHashMap<Integer, String>();
		LinkedHashMap<Integer, String> oldConfig = new LinkedHashMap<Integer, String>();
		//init oldConfig from this.curIndexConfig
		for (int i = 0; i < this.curIndexConfig.size(); i++) {
			oldConfig.put(i, this.curIndexConfig.get(i));
		}
		//init newConfig
		for (int i = 0; i < this.form2.getSize(); i++) {
			String columnName = this.form2.getIndexColumnNameAt(i).getValue();
			String indexType = AmiCenterManagerUtils.toIndexType((short) this.form2.getIndexTypeAt(i).getValue());
			newConfig.put(i, columnName + " " + indexType);
		}
		diffPair.setA(oldConfig);
		diffPair.setB(newConfig);
		return diffPair;
	}
	private void onFieldChanged(FormPortletField field) {
		if (AmiCenterManagerIndexConfigForm.TYPE_COLNAME.equals(field.getName()) || AmiCenterManagerIndexConfigForm.TYPE_INDEXTYPE.equals(field.getName())) {
			this.testButton.setEnabled(true);
			this.resetButton.setEnabled(true);
			this.diffButton.setEnabled(true);
			return;
		}
		if (field == this.constraintField)
			this.onConstraintFieldChanged();
		boolean hadNoChanges = this.editedFields.isEmpty() && this.editedSelectFields.isEmpty();
		String value = null;
		Object rawValue = field.getValue();
		if (rawValue instanceof String)
			value = SH.trim((String) rawValue);
		else if (rawValue instanceof Short) {//&& AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT.equals(field.getName())
			short typeCode = (short) rawValue;
			FormPortletSelectField<Short> sf = (FormPortletSelectField) field;
			value = sf.getOption(typeCode).getName();
		}

		String orig = fieldCache.get(field.getName());
		if (field instanceof FormPortletTextField) {
			FormPortletTextField tf = (FormPortletTextField) field;
			if (OH.eq(value, orig) || (SH.isnt(field.getValue()) && orig == null)) {
				this.editedFields.remove(tf);
				formatFieldTitle(tf, false);
			} else {
				this.editedFields.add(tf);
				formatFieldTitle(tf, true);
			}
		} else if (field instanceof FormPortletSelectField) {
			FormPortletSelectField psf = (FormPortletSelectField) field;
			if (OH.eq(value, orig) || (SH.isnt(field.getValue()) && orig == null)) {
				this.editedSelectFields.remove(psf);
				formatFieldTitle(psf, false);
			} else {
				this.editedSelectFields.add(psf);
				formatFieldTitle(psf, true);
			}
		}
		boolean hasNoChanges = this.editedFields.isEmpty() && this.editedSelectFields.isEmpty();
		if (hasNoChanges != hadNoChanges) {
			this.testButton.setEnabled(!hasNoChanges);
			this.resetButton.setEnabled(!hasNoChanges);
			this.diffButton.setEnabled(!hasNoChanges);
		}
	}

	private void onConstraintFieldChanged() {
		//only enable autogen if:
		//1. the constraint = "PRIMARY"
		//2. only one column
		if ((short) constraintField.getValue() == AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_CODE_PRIMARY && this.form2.getSize() == 1)
			this.autogenField.setVisible(true);
		else {
			this.autogenField.setVisible(false);
			//when the constraint is not primary, autogen should always be reset to NONE
			this.autogenField.setValue(AmiCenterEntityConsts.AUTOGEN_TYPE_CODE_NONE);
			onFieldChanged(this.autogenField);
		}

	}

	private static void formatFieldTitle(FormPortletField<?> field, boolean hasChanged) {
		if (hasChanged)
			field.setTitle(AmiCenterEntityConsts.CHANGED_FIELD_ANNOTATION_HTML + field.getTitle());
		else
			field.setTitle(field.getTitle().replace(AmiCenterEntityConsts.CHANGED_FIELD_ANNOTATION_HTML, ""));

	}
	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

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
			//Go to the column tab to grab all the column names
			AmiCenterManagerEditColumnPortlet colPortlet = null;
			for (Portlet p : this.owningTab.getChildren().values()) {
				if (p instanceof AmiCenterManagerEditColumnPortlet)
					colPortlet = (AmiCenterManagerEditColumnPortlet) p;
			}
			SmartTable st = colPortlet.getColumnTable();
			List<String> colNames = new ArrayList<String>();
			//TODO:Can we use columnarcolumn instead?
			for (int i = 0; i < st.getSize(); i++) {
				colNames.add((String) st.get(i, "columnName"));
			}

			this.form2.createColNameFieldsContextMenu(field, r, colNames);
			return r;
		}
		return null;
	}
	@Override
	public void onContextMenu(FastWebTree tree, String action) {
		// TODO Auto-generated method stub

	}
	@Override
	public void onNodeClicked(FastWebTree tree, WebTreeNode node) {
		if (!this.editedFields.isEmpty() || !this.editedSelectFields.isEmpty()) {
			getManager().showAlert("You are in the middle of an edit, please <B>Test</B> or <B>Reset</B> changes first");
			return;
		}
		if (node == null || node == this.treeNodeIndexes) {
			return;
		}
		//enable preview button
		this.previewButton.setEnabled(true);
		AmiCenterGraphNode_Index target = (AmiCenterGraphNode_Index) node.getData();
		//parse the index node name: nodename in the format: [tablename]::[indexname]
		String tableName = AmiCenterManagerUtils.indexNameToTableAndIndex(target.getLabel()).getA();
		String indexName = AmiCenterManagerUtils.indexNameToTableAndIndex(target.getLabel()).getB();
		//query the backend to init the editor
		prepareRequestToBackend("SHOW FULL INDEXES WHERE IndexName==\"" + indexName + "\"" + " && " + "TableName==\"" + tableName + "\";");

	}
	@Override
	public void onCellMousedown(FastWebTree tree, WebTreeNode start, FastWebTreeColumn col) {
		// TODO Auto-generated method stub

	}
	@Override
	public void onNodeSelectionChanged(FastWebTree fastWebTree, WebTreeNode node) {
		// TODO Auto-generated method stub

	}
	@Override
	public int compare(WebTreeNode o1, WebTreeNode o2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void onOptionFieldAdded() {
		updateSuggestedSizeOfWhereFieldForm();
		//check if there are changes on the index config
		if (form2.getSize() != this.curIndexSize) {
			this.testButton.setEnabled(true);
			this.resetButton.setEnabled(true);
			this.diffButton.setEnabled(true);
		} else {
			this.testButton.setEnabled(false);
			this.resetButton.setEnabled(false);
			this.diffButton.setEnabled(false);
		}
	}

	@Override
	public void onOptionFieldRemoved() {
		updateSuggestedSizeOfWhereFieldForm();
		if (form2.getSize() != this.curIndexSize) {
			this.testButton.setEnabled(true);
			this.resetButton.setEnabled(true);
			this.diffButton.setEnabled(true);
		}
	}

	@Override
	public void onOptionFieldEdited(FormPortletField<?> field) {
		onFieldChanged(field);
	}

	private void updateSuggestedSizeOfWhereFieldForm() {
		this.gridForm.setRowSize(1, form2.getSuggestedHeight(null));
	}

	public String previewScript() {
		StringBuilder sb = new StringBuilder("CREATE INDEX ");
		sb.append(parseValueForRequiredField(nameField));
		sb.append(" ON ").append(parseValueForRequiredField(this.onField));
		sb.append('(');
		for (int i = 0; i < this.form2.getSize(); i++) {
			sb.append(form2.getIndexColumnNameAt(i).getValue()).append(' ');
			sb.append(AmiCenterManagerUtils.toIndexType((short) form2.getIndexTypeAt(i).getValue()));
			if (i != this.form2.getSize() - 1)
				sb.append(',');
		}
		sb.append(')');
		sb.append(" USE ").append(prepareUseClause());
		sb.append(";");
		return sb.toString();
	}

	private String prepareUseClause() {
		StringBuilder script = new StringBuilder();
		if (this.constraintField.getValue() != AmiCenterEntityConsts.INDEX_CONSTRAINT_TYPE_CODE_NONE)
			script.append(AmiCenterEntityConsts.OPTION_NAME_INDEX_CONSTRAINT).append(" = ")
					.append(SH.doubleQuote(AmiCenterManagerUtils.toIndexConstraint(this.constraintField.getValue()))).append(" ");
		if (this.autogenField.getValue() != AmiCenterEntityConsts.AUTOGEN_TYPE_CODE_NONE)
			script.append(AmiCenterEntityConsts.OPTION_NAME_INDEX_AUTOGEN).append(" = ").append(SH.doubleQuote(AmiCenterManagerUtils.toIndexAutogen(this.autogenField.getValue())));
		return script.toString();
	}

	private static String parseValueForRequiredField(FormPortletField<?> f) {
		if (SH.isnt(f.getValue()))
			return AmiCenterEntityConsts.REQUIRED_FEILD_WARNING;
		return Caster_String.INSTANCE.cast(f.getValue());

	}
}
