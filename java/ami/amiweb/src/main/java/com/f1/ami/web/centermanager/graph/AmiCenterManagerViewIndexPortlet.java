package com.f1.ami.web.centermanager.graph;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebConsts;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Table;
import com.f1.base.Action;
import com.f1.base.Column;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.base.TableListenable;
import com.f1.container.ResultMessage;
import com.f1.suite.web.fastwebcolumns.FastWebColumns;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.portal.Portlet;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.DividerPortlet;
import com.f1.suite.web.portal.impl.FastTablePortlet;
import com.f1.suite.web.portal.impl.FastTreePortlet;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.HtmlPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.table.WebColumn;
import com.f1.suite.web.table.WebTable;
import com.f1.suite.web.table.WebTableColumnContextMenuFactory;
import com.f1.suite.web.table.fast.FastWebTable;
import com.f1.suite.web.table.impl.BasicWebColumn;
import com.f1.suite.web.tree.WebTreeContextMenuListener;
import com.f1.suite.web.tree.WebTreeManager;
import com.f1.suite.web.tree.WebTreeNode;
import com.f1.suite.web.tree.impl.FastWebTree;
import com.f1.suite.web.tree.impl.FastWebTreeColumn;
import com.f1.utils.CH;
import com.f1.utils.SH;
import com.f1.utils.sql.Tableset;
import com.f1.utils.structs.table.BasicTable;

public class AmiCenterManagerViewIndexPortlet extends GridPortlet implements WebTableColumnContextMenuFactory, FormPortletListener, WebTreeContextMenuListener {
	final private FastTreePortlet indexTree;
	final private WebTreeManager treeMgr;
	final private WebTreeNode treeRoot;
	final private AmiWebService service;
	private Portlet indexConfigTable;
	private FormPortlet form;
	private WebTreeNode rootNode;
	private DividerPortlet viewDataDivider;

	public AmiCenterManagerViewIndexPortlet(PortletConfig config, AmiCenterGraphNode_Table node) {
		super(config);
		this.service = AmiWebUtils.getService(config.getPortletManager());
		this.form = new FormPortlet(generateConfig());
		form.addButton(new FormPortletButton("Close"));
		form.addFormPortletListener(this);
		this.indexTree = new FastTreePortlet(generateConfig());
		this.indexTree.addOption(FastTreePortlet.OPTION_SEARCH_BUTTONS_COLOR, "#007608");
		this.indexTree.addOption(FastTreePortlet.OPTION_GRIP_COLOR, "_bg=#ffffff");
		this.indexTree.addOption(FastTreePortlet.OPTION_SCROLL_BUTTON_COLOR, "_bg=#ffffff");
		this.indexTree.addOption(FastTreePortlet.OPTION_SCROLL_ICONS_COLOR, "#007608");
		this.indexTree.addOption(FastTreePortlet.OPTION_SEARCH_BAR_HIDDEN, true);
		this.indexTree.addOption(FastTreePortlet.OPTION_HEADER_DIVIDER_HIDDEN, true);
		this.indexTree.addOption(FastTreePortlet.OPTION_HEADER_BAR_HIDDEN, true);
		this.indexTree.addOption(FastTreePortlet.OPTION_CELL_RIGHT_DIVIDER, 0);
		this.indexTree.addOption(FastTreePortlet.OPTION_CELL_BOTTOM_DIVIDER, 0);
		this.indexTree.addOption(FastTreePortlet.OPTION_BACKGROUND_STYLE, "_bg=#eeeeee");
		this.indexTree.addOption(FastTreePortlet.OPTION_GRAY_BAR_STYLE, "_bg=#eeeeee");
		this.treeMgr = indexTree.getTreeManager();
		this.indexTree.getTree().addMenuContextListener(this);
		this.indexTree.getTree().setRootLevelVisible(false);
		this.treeRoot = treeMgr.getRoot();
		this.treeRoot.setIconCssStyle("_bgi=url(" + AmiWebConsts.ICON_FILES + ")");
		this.treeRoot.setName("Indexes");

		this.rootNode = treeMgr.createNode("Indexes", treeMgr.getRoot(), true).setIconCssStyle("_bgi=url(" + AmiWebConsts.ICON_FOLDER + ")");

		sendQueryToBackend("SHOW INDEXES WHERE TableName==\"" + node.getLabel() + "\";");
		this.indexConfigTable = new HtmlPortlet(generateConfig());

		viewDataDivider = new DividerPortlet(generateConfig(), true);
		viewDataDivider.setThickness(1);
		viewDataDivider.addChild(indexTree);
		viewDataDivider.addChild(indexConfigTable);
		this.addChild(viewDataDivider, 0, 0, 1, 1);
		viewDataDivider.setExpandBias(0, 1);
		this.addChild(form, 0, 1, 1, 1);
		this.setRowSize(1, 50);
		viewDataDivider.setOffsetFromTopPx(250);
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
		List<WebTreeNode> sel = tree.getSelected();
		FastWebTable ft = ((FastTablePortlet) this.indexConfigTable).getTable();
		if (sel.contains(rootNode))
			ft.clearFilters(CH.s("0"));
		else {
			Set<String> filterNames = CH.s();
			for (WebTreeNode n : sel)
				filterNames.add(n.getName());
			ft.setFilteredIn("0", filterNames);
		}

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
	public WebMenu createColumnMenu(WebTable table, WebColumn column, WebMenu defaultMenu) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public WebMenu createColumnMenu(WebTable table, WebMenu defaultMenu) {
		// TODO Auto-generated method stub
		return null;
	}

	//The abilities to query the backend
	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		Action a = result.getRequestMessage().getAction();
		String query = null;
		Tableset results;
		if (a instanceof AmiCenterQueryDsRequest) {
			AmiCenterQueryDsRequest request = (AmiCenterQueryDsRequest) a;
			query = request.getQuery();
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		if (response.getOk()) {
			List<Table> tables = response.getTables();
			if (tables.size() == 1) {
				Table t = tables.get(0);
				FastTablePortlet tablePortlet = new FastTablePortlet(generateConfig(), (TableListenable) new BasicTable(t), "foo");
				this.viewDataDivider.replaceChild(this.indexConfigTable.getPortletId(), this.indexConfigTable = tablePortlet);
				rootNode.setSelected(true);
				tablePortlet.addOption(FastTablePortlet.OPTION_HEADER_ROW_HEIGHT, 32);
				rootNode.setData(tablePortlet);
				int i = 0;
				for (Column c : t.getColumns()) {
					Class<?> type = c.getType();
					String stype = service.getMethodFactory().forType(type);
					BasicWebColumn bc = tablePortlet.getTable().addColumnAt(true, c.getId() + "<BR><I>" + stype, c.getId(), this.service.getFormatterManager().getFormatter(type),
							i++);
				}
				tablePortlet.autoSizeAllColumns();
				getManager().onPortletAdded(tablePortlet);

				for (Row r : t.getRows()) {
					String indexName = (String) r.get("IndexName");
					WebTreeNode node = treeMgr.createNode(indexName, rootNode, false).setIconCssStyle("_bgi=url(" + AmiWebConsts.ICON_DOT + ")");
					//node.setData(data)
				}
			}

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
		//		request.setQuerySessionId(this.sessionId);
		service.sendRequestToBackend(this, request);
	}

}
