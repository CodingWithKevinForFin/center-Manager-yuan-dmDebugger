package com.f1.ami.web.centermanager.editors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Trigger;
import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.fastwebcolumns.FastWebColumns;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.DividerPortlet;
import com.f1.suite.web.portal.impl.FastTreePortlet;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.HtmlPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuFactory;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuListener;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.tree.WebTreeContextMenuListener;
import com.f1.suite.web.tree.WebTreeNode;
import com.f1.suite.web.tree.impl.FastWebTree;
import com.f1.suite.web.tree.impl.FastWebTreeColumn;
import com.f1.utils.SH;
import com.f1.utils.casters.Caster_String;
import com.f1.utils.structs.LongKeyMap;
import com.f1.utils.structs.table.derived.DerivedCellCalculator;

public class AmiCenterManagerTriggerScirptTreePortlet extends GridPortlet implements Comparator<WebTreeNode>, WebTreeContextMenuListener, FormPortletContextMenuFactory,
		FormPortletListener, FormPortletContextMenuListener, AmiWebCompilerListener, AmiWebDomObjectDependency {
	public static final String DEFAULT_DS_NAME = "AMI";
	public static final byte DEFAULT_PERMISSION = (byte) 15;
	//Backend config
	public static final int DEFAULT_LIMIT = 10000;
	public static final int DEFAULT_TIMEOUT = 60000;
	private long sessionId = -1;
	private static final String BG_GREY = "_bg=#4c4c4c";

	private LongKeyMap<List<WebTreeNode>> nodesByGraphId = new LongKeyMap<List<WebTreeNode>>();
	final private AmiWebService service;
	final private DividerPortlet divider;
	final private FastTreePortlet tree;
	public HashMap<String, AmiCenterGraphNode_Trigger> triggerNodeByNames = new HashMap<String, AmiCenterGraphNode_Trigger>();

	//form
	private GridPortlet triggerEditorGrid;
	private InnerPortlet triggerEditorPanel;
	final private HtmlPortlet blankPreview;
	private AmiCenterManagerEditTriggerPortlet triggerEditor;

	private WebTreeNode treeNodeTriggers;

	public AmiCenterManagerTriggerScirptTreePortlet(PortletConfig config, Map<String, AmiCenterGraphNode_Trigger> triggerBinding) {
		super(config);
		this.service = AmiWebUtils.getService(getManager());
		this.blankPreview = new HtmlPortlet(generateConfig());

		this.tree = new FastTreePortlet(generateConfig());
		this.tree.getTree().setComparator(this);
		this.tree.getTree().addMenuContextListener(this);
		//add default form and dialog style for this.tree
		this.tree.setFormStyle(AmiWebUtils.getService(getManager()).getUserFormStyleManager());
		this.tree.setDialogStyle(AmiWebUtils.getService(getManager()).getUserDialogStyleManager());
		this.tree.getTree().setRootLevelVisible(false);
		buildTree(triggerBinding);

		this.triggerEditor = new AmiCenterManagerEditTriggerPortlet(generateConfig(), true);
		this.triggerEditor.enableEdit(false);
		this.triggerEditorGrid = new GridPortlet(generateConfig());
		this.triggerEditorPanel = this.triggerEditorGrid.addChild(blankPreview.setCssClass(BG_GREY), 0, 0, 2, 2);

		this.divider = new DividerPortlet(generateConfig(), true, this.tree, this.triggerEditorGrid);
		this.divider.setOffsetFromTopPx(300);
		this.addChild(divider);

		this.service.addCompilerListener(this);
		this.service.getDomObjectsManager().addGlobalListener(this);

		sendAuth();
	}

	@Override
	public void onClosed() {
		this.service.removeCompilerListener(this);
		this.service.getDomObjectsManager().removeGlobalListener(this);
		super.onClosed();
	}

	private void buildTree(Map<String, AmiCenterGraphNode_Trigger> triggerBinding) {
		this.tree.clear();
		this.nodesByGraphId.clear();
		this.treeNodeTriggers = createNode(this.tree.getRoot(), "Triggers", AmiWebConsts.CENTER_GRAPH_NODE_TRIGGER, null);
		for (Entry<String, AmiCenterGraphNode_Trigger> e : triggerBinding.entrySet()) {
			String triggerName = e.getKey();
			AmiCenterGraphNode_Trigger trigger = e.getValue();
			triggerNodeByNames.put(triggerName, trigger);
			createNode(this.treeNodeTriggers, trigger);
		}
	}

	private WebTreeNode createNode(WebTreeNode parent, String title, String icon, Object data) {
		WebTreeNode r = this.tree.createNode(title, parent, false, data);
		r.setIconCssStyle(icon == null ? null : "_bgi=url('" + icon + "')");
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

	@Override
	public void onUserDblclick(FastWebColumns columns, String action, Map<String, String> properties) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onContextMenu(FastWebTree tree, String action) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onNodeClicked(FastWebTree tree, WebTreeNode node) {
		//		if (!this.editedFields.isEmpty() || !this.editedScripts.isEmpty() || !this.editedSelectFields.isEmpty() || !this.editedCheckboxFields.isEmpty()) {
		//			getManager().showAlert("You are in the middle of an edit, please <B>Test</B> or <B>Reset</B> changes first");
		//			return;
		//		}
		if (node == null || node == this.treeNodeTriggers)
			return;

		AmiCenterGraphNode_Trigger target = (AmiCenterGraphNode_Trigger) node.getData();
		//query the backend to init the editor
		sendQueryToBackend("DESCRIBE TRIGGER " + target.getLabel() + ";");
	}

	@Override
	public void onCellMousedown(FastWebTree tree, WebTreeNode start, FastWebTreeColumn col) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onNodeSelectionChanged(FastWebTree fastWebTree, WebTreeNode node) {
	}

	@Override
	public int compare(WebTreeNode o1, WebTreeNode o2) {
		// TODO Auto-generated method stub
		return 0;
	}

	//////////////////////
	//The abilities to query the backend
	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		if (response.getOk() && response.getTables() != null && response.getTables().size() == 1) {
			Table t = response.getTables().get(0);
			Row r = t.getRow(0);
			String triggerSql = Caster_String.INSTANCE.cast(r.get("SQL"));
			String name = SH.beforeFirst(SH.afterFirst(triggerSql, "CREATE TRIGGER "), " ");
			AmiCenterGraphNode_Trigger correlationNode = triggerNodeByNames.get(name);
			this.triggerEditor = new AmiCenterManagerEditTriggerPortlet(generateConfig(), triggerSql, correlationNode);
			this.getManager().onPortletAdded(this.triggerEditor);
			this.triggerEditorPanel.setPortlet(this.triggerEditor);

		}
	}

	public AmiCenterQueryDsRequest prepareRequest() {
		AmiCenterQueryDsRequest request = getManager().getTools().nw(AmiCenterQueryDsRequest.class);

		request.setLimit(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_LIMIT);
		request.setTimeoutMs(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_TIMEOUT);
		request.setQuerySessionKeepAlive(true);
		request.setIsTest(false);
		request.setAllowSqlInjection(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_ALLOW_SQL_INJECTION);
		request.setInvokedBy(service.getUserName());
		request.setSessionVariableTypes(null);
		request.setSessionVariables(null);
		request.setPermissions(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PERMISSION);
		request.setType(AmiCenterQueryDsRequest.TYPE_QUERY);
		request.setOriginType(AmiCenterQueryDsRequest.ORIGIN_FRONTEND_SHELL);
		request.setDatasourceName(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_DS_NAME);
		return request;
	}

	protected void sendQueryToBackend(String query) {
		if (SH.isnt(query))
			return;
		AmiCenterQueryDsRequest request = prepareRequest();
		if (request == null)
			return;
		request.setQuery(query);
		request.setQuerySessionId(this.sessionId);
		service.sendRequestToBackend(this, request);
	}

	////////////////////////

	private void sendAuth() {
		AmiCenterQueryDsRequest request = prepareRequest();
		if (request == null)
			return;
		service.sendRequestToBackend(this, request);
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
		// TODO Auto-generated method stub

	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

	}

	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		// TODO Auto-generated method stub
		return null;
	}

}
