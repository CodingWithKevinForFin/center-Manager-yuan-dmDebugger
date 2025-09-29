package com.f1.ami.web.centermanager.editors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.f1.ami.amicommon.AmiUtils;
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
import com.f1.ami.web.centermanager.portlets.AmiCenterManagerReviewApplyScriptPortlet;
import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.fastwebcolumns.FastWebColumns;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenuLink;
import com.f1.suite.web.portal.Portlet;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.PortletManager;
import com.f1.suite.web.portal.impl.ConfirmDialogPortlet;
import com.f1.suite.web.portal.impl.DividerPortlet;
import com.f1.suite.web.portal.impl.FastTreePortlet;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.HtmlPortlet;
import com.f1.suite.web.portal.impl.TabPortlet;
import com.f1.suite.web.portal.impl.TreeStateCopierIdGetter;
import com.f1.suite.web.portal.impl.GridPortlet.InnerPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuFactory;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuListener;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.suite.web.portal.style.PortletStyleManager_Dialog;
import com.f1.suite.web.tree.WebTreeContextMenuFactory;
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

public class AmiCenterManagerIndexScirptTreePortlet extends GridPortlet implements Comparator<WebTreeNode>, WebTreeContextMenuListener,
		FormPortletListener, WebTreeContextMenuFactory, FormPortletContextMenuListener, TreeStateCopierIdGetter {
	//create index myindex on accounts(id hash) use constraint="";
	public static final String DEFAULT_DS_NAME = "AMI";
	public static final byte DEFAULT_PERMISSION = (byte) 15;
	//Backend config
	public static final int DEFAULT_LIMIT = 10000;
	public static final int DEFAULT_TIMEOUT = 60000;
	private long sessionId = -1;
	private static final String BG_GREY = "_bg=#4c4c4c";

	private LongKeyMap<List<WebTreeNode>> nodesByGraphId = new LongKeyMap<List<WebTreeNode>>();

	final private DividerPortlet divider;
	final private FastTreePortlet tree;

	private TabPortlet owningTab;
	
	final private GridPortlet indexEditorGrid;
	final private InnerPortlet indexEditorPanel;
	final private HtmlPortlet blankPreview;
    final private AmiCenterManagerEditIndexPortlet indexEditor;
    final private String tableName;
    final private AmiWebService service;

	private WebTreeNode treeNodeIndexes;

	public AmiCenterManagerIndexScirptTreePortlet(PortletConfig config, Map<String, AmiCenterGraphNode_Index> indexBinding, String sql, TabPortlet owningTab, String tableName) {
		super(config);
		this.service = AmiWebUtils.getService(getManager());
		this.owningTab = owningTab;
		this.blankPreview = new HtmlPortlet(generateConfig());
		this.tableName = tableName;
		
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
		this.tree.getTree().setContextMenuFactory(this);
		
		buildTree(indexBinding);
		
		this.indexEditor = new AmiCenterManagerEditIndexPortlet(generateConfig(), sql);
		this.indexEditor.enableEdit(false);
		this.indexEditorGrid = new GridPortlet(generateConfig());
		this.indexEditorPanel = this.indexEditorGrid.addChild(blankPreview.setCssClass(BG_GREY), 0, 0, 2, 2);

		this.divider.addChild(this.tree);
		this.divider.addChild(this.indexEditor);

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
	public void onUserDblclick(FastWebColumns columns, String action, Map<String, String> properties) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public Object getId(WebTreeNode node) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField node) {
		

	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public void onContextMenu(FastWebTree tree, String action) {
		if("add_index".equals(action)) {
			AmiCenterManagerEditIndexPortlet editor = (AmiCenterManagerEditIndexPortlet) service.getAmiCenterManagerEditorsManager().showAddCenterObjectPortlet(AmiCenterGraphNode.TYPE_INDEX);
			editor.setIndexOn(tableName);
		}

	}
	@Override
	public void onNodeClicked(FastWebTree tree, WebTreeNode node) {
		if (indexEditor.hasEdit()) {
			getManager().showAlert("You are in the middle of an edit, please <B>Test</B> or <B>Reset</B> changes first");
			return;
		}
		if (node == null || node == this.treeNodeIndexes) {
			return;
		}
		AmiCenterGraphNode_Index target = (AmiCenterGraphNode_Index) node.getData();
		//parse the index node name: nodename in the format: [tablename]::[indexname]
		String tableName = target.getBindingTable().getLabel();
		String indexName = SH.afterFirst(target.getLabel(), tableName + "::");
		//query the backend to init the editor
		indexEditor.sendQueryToBackend("DESCRIBE INDEX " +  AmiUtils.escapeVarName(indexName) + " ON " +  AmiUtils.escapeVarName(tableName));
		indexEditor.sendQueryToBackend("SHOW FULL COLUMNS WHERE TableName == \"" + AmiUtils.escapeVarName(tableName) + "\" ORDER BY Position;");	


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


	
	
	public void setSelectedIndexNode(String indexname) {
		WebTreeNode idxNode = null;
		for(WebTreeNode n: this.tree.getTree().getNodes()){
			if(n.getName().equals(indexname)) {
				idxNode = n;
				break;
			}
				
		}
		if(idxNode == null)
			throw new NullPointerException("index not found: " + indexname);
		this.tree.getTreeManager().setAllExpanded(true);
		idxNode.setSelected(true);
		//this.tree.getTreeManager().setActiveSelectedNode(idxNode);
		onNodeClicked(tree.getTree(), idxNode);
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
	public WebMenu createMenu(FastWebTree fastWebTree, List<WebTreeNode> selected) {
		BasicWebMenu menu = new BasicWebMenu();
		menu.addChild(new BasicWebMenuLink("Add Index", true, "add_index"));
		return menu;
	}



	@Override
	public boolean formatNode(WebTreeNode node, StringBuilder sink) {
		// TODO Auto-generated method stub
		return false;
	}
}
