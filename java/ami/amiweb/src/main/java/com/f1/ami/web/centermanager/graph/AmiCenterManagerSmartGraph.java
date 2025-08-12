package com.f1.ami.web.centermanager.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.logging.Logger;

import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.centermanager.graph.AmiWebCenterGraphManager;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Dbo;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Index;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Method;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Procedure;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Table;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Timer;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Trigger;
import com.f1.ami.web.centermanager.portlets.AmiWebCenterManagerPortlet;
import com.f1.ami.web.graph.AmiWebGraphNode;
import com.f1.ami.web.graph.AmiWebGraphNode_Link;
import com.f1.base.IterableAndSize;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.portal.impl.WebMenuListener;
import com.f1.suite.web.portal.impl.visual.GraphContextMenuFactory;
import com.f1.suite.web.portal.impl.visual.GraphListener;
import com.f1.suite.web.portal.impl.visual.GraphPortlet;
import com.f1.suite.web.portal.impl.visual.GraphPortlet.Edge;
import com.f1.suite.web.portal.impl.visual.GraphPortlet.Node;
import com.f1.utils.CH;
import com.f1.utils.LH;
import com.f1.utils.MH;
import com.f1.utils.OH;
import com.f1.utils.SH;
import com.f1.utils.concurrent.IdentityHashSet;
import com.f1.utils.structs.LongKeyMap;

public class AmiCenterManagerSmartGraph implements GraphListener, GraphContextMenuFactory, WebMenuListener {
	private static final Logger log = LH.get();

	//TODO: replace these with new icons
	private static final String STYLE_TABLE = "_cna=graph_node_table";//"_cna=graph_node_rt_chartpanel";//"_cna=graph_node_table";
	private static final String STYLE_TRIGGER = "_cna=graph_node_trigger";
	private static final String STYLE_TIMER = "_cna=graph_node_timer";

	private static final String STYLE_PROCEDURE = "_cna=graph_node_procedure";
	private static final String STYLE_METHOD = "_cna=graph_node_method";
	private static final String STYLE_DBO = "_cna=graph_node_dbo";
	private static final String STYLE_INDEX = "_cna=graph_node_index";

	private static final int LEFT_PADDING = 140;
	private static final int TOP_PADDING = 100;
	private static final int X_SPACING = 120;
	private static final int Y_SPACING = 120;
	private static final int NODE_WIDTH = 100;
	private static final int NODE_HEIGHT = 60;
	private GraphPortlet graph;
	private AmiWebService service;
	private final LongKeyMap<Node> graphNodes = new LongKeyMap<Node>();
	private int minXPos[];
	private final LongKeyMap<Integer> depths = new LongKeyMap<Integer>();
	private final IdentityHashSet<AmiCenterGraphNode> remaining = new IdentityHashSet<AmiCenterGraphNode>();
	private int maxDepth;
	private LongKeyMap<AmiCenterGraphNode> allNodes = new LongKeyMap<AmiCenterGraphNode>();
	private IdentityHashSet<AmiCenterGraphNode> origNodes = new IdentityHashSet<AmiCenterGraphNode>();
	private IdentityHashSet<AmiCenterGraphNode> toSelect = new IdentityHashSet<AmiCenterGraphNode>();
	private AmiWebCenterManagerPortlet owner;
	private boolean allowModification;
	private static byte WALK_SOURCE = 1;//source is always table
	private static byte WALK_TARGET = 2;//target can be triggers or index

	private final Comparator<AmiCenterGraphNode> comparator = new Comparator<AmiCenterGraphNode>() {

		@Override
		public int compare(AmiCenterGraphNode o1, AmiCenterGraphNode o2) {
			Integer d1 = depths.get(o1.getUid());
			Integer d2 = depths.get(o2.getUid());
			int n = OH.compare(d1, d2, true);
			return n == 0 ? AmiWebCenterGraphManager.COMPARATOR_ID.compare(o1, o2) : n;
		}
	};

	public AmiCenterManagerSmartGraph(AmiWebCenterManagerPortlet owner, AmiWebService service, GraphPortlet graph, boolean allowModification) {
		this.graph = graph;
		this.owner = owner;
		this.allowModification = allowModification;
		this.graph.setMenuFactory(this);
		this.service = service;
		this.graph.addGraphListener(this);
		this.graph.setSnapSize(5);
		this.graph.setGridSize(20);
	}

	public void buildGraph(IdentityHashSet<AmiCenterGraphNode> origNodes, IdentityHashSet<AmiCenterGraphNode> toSelect) {
		if (this.origNodes.equals(origNodes) && this.toSelect.equals(toSelect))
			return;
		this.origNodes.clear();
		this.origNodes.addAll(origNodes);
		this.toSelect.clear();
		this.toSelect.addAll(toSelect);
		this.owner.onGraphNeedsRebuild();
	}
	public void rebuild() {
		IdentityHashMap<AmiCenterGraphNode, Byte> allNodes = new IdentityHashMap<AmiCenterGraphNode, Byte>();
		for (AmiCenterGraphNode i : this.origNodes)
			walkNodes(i, allNodes, (byte) (WALK_SOURCE | WALK_TARGET));
		this.allNodes.clear();
		for (AmiCenterGraphNode i : allNodes.keySet())
			this.allNodes.put(i.getUid(), i);
		this.graphNodes.clear();
		this.graph.clear();
		this.depths.clear();
		this.remaining.clear();
		for (AmiCenterGraphNode i : this.allNodes.values())
			this.remaining.add(i);

		//TODO:REMOVE this.remaining? remaining should be identical to this.allNodes
		int depth = 0;

		List<AmiCenterGraphNode_Table> tables = new ArrayList<AmiCenterGraphNode_Table>();
		List<AmiCenterGraphNode_Trigger> triggers = new ArrayList<AmiCenterGraphNode_Trigger>();
		List<AmiCenterGraphNode_Index> indexes = new ArrayList<AmiCenterGraphNode_Index>();

		List<AmiCenterGraphNode_Procedure> procedures = new ArrayList<AmiCenterGraphNode_Procedure>();
		List<AmiCenterGraphNode_Timer> timers = new ArrayList<AmiCenterGraphNode_Timer>();
		List<AmiCenterGraphNode_Dbo> dbos = new ArrayList<AmiCenterGraphNode_Dbo>();
		List<AmiCenterGraphNode_Method> methods = new ArrayList<AmiCenterGraphNode_Method>();

		for (AmiCenterGraphNode i : this.allNodes.values()) {
			depth = Math.max(depth, determineDepth(i));
			if (i.getType() == AmiCenterGraphNode.TYPE_TABLE)
				tables.add((AmiCenterGraphNode_Table) i);
			else if (i.getType() == AmiCenterGraphNode.TYPE_INDEX)
				indexes.add((AmiCenterGraphNode_Index) i);
			else if (i.getType() == AmiCenterGraphNode.TYPE_TRIGGER)
				triggers.add((AmiCenterGraphNode_Trigger) i);
			else if (i.getType() == AmiCenterGraphNode.TYPE_PROCEDURE)
				procedures.add((AmiCenterGraphNode_Procedure) i);
			else if (i.getType() == AmiCenterGraphNode.TYPE_TIMER)
				timers.add((AmiCenterGraphNode_Timer) i);
			else if (i.getType() == AmiCenterGraphNode.TYPE_METHOD)
				methods.add((AmiCenterGraphNode_Method) i);
			else if (i.getType() == AmiCenterGraphNode.TYPE_DBO)
				dbos.add((AmiCenterGraphNode_Dbo) i);
		}

		this.maxDepth = depth;
		minXPos = new int[depth + 1];
		//Start with tables
		for (AmiCenterGraphNode_Table i : sort(tables)) {
			if (i.getTargetIndexes().isEmpty() && i.getTargetTriggers().isEmpty())
				continue;
			Node node = addNode(0, i);
			if (node == null)
				continue;
			List<Node> childs = new ArrayList<Node>();
			//			for (AmiCenterGraphNode_Trigger j : i.getTargetTriggers().values())
			//				CH.addSkipNull(childs, addToGraph(getX(node), j));
			//			center(node, childs);
		}

		//then for tables without indexes or triggers
		for (AmiCenterGraphNode i : CH.l(remaining)) {
			addNode(0, i);
		}

		//build links
		for (AmiCenterGraphNode i : allNodes.keySet()) {
			Node sourceNode = graphNodes.get(i.getUid());
			switch (i.getType()) {
				case AmiCenterGraphNode.TYPE_TABLE:
					AmiCenterGraphNode_Table t = (AmiCenterGraphNode_Table) i;
					//build links for triggers
					for (AmiCenterGraphNode_Trigger j : t.getTargetTriggers().values()) {
						Node targetNode = graphNodes.get(j.getUid());
						if (targetNode != null) {
							addDataEdge(sourceNode, targetNode);
						}
					}
					//build links for indexes
					for (AmiCenterGraphNode_Index j : t.getTargetIndexes().values()) {
						Node targetNode = graphNodes.get(j.getUid());
						if (targetNode != null) {
							addDataEdge(sourceNode, targetNode);
						}
					}
					break;
			}
		}
	}

	private void addDataEdge(Node sourceNode, Node targetNode) {
		Edge edge = this.graph.addEdge(sourceNode.getId(), targetNode.getId());
		edge.setColor("#AEAEAE");
		edge.setDirection(GraphPortlet.DIRECTION_FORWARD);
	}

	private <T extends AmiCenterGraphNode> Collection<T> sort(Collection<T> in) {
		if (in.isEmpty())
			return Collections.EMPTY_LIST;
		ArrayList<T> r = new ArrayList<T>();
		for (T i : in)
			if (this.depths.containsKey(i.getUid()) || i instanceof AmiWebGraphNode_Link)
				r.add(i);
		Collections.sort(r, comparator);
		return r;
	}

	private Node addNode(int minx, AmiCenterGraphNode i) {
		if (!this.remaining.remove(i))
			return null;
		int y = depths.get(i.getUid());
		String style;
		String name = i.getLabel();
		//String description = i.getDescription();
		//		if (SH.is(description))
		//			name = name + "<BR>(" + description + ")";
		switch (i.getType()) {
			case AmiCenterGraphNode.TYPE_TABLE:
				style = STYLE_TABLE;
				break;
			case AmiCenterGraphNode.TYPE_TIMER: {
				style = STYLE_TIMER;
				break;
			}
			case AmiCenterGraphNode.TYPE_TRIGGER: {
				style = STYLE_TRIGGER;
				break;
			}
			case AmiCenterGraphNode.TYPE_PROCEDURE: {
				style = STYLE_PROCEDURE;
				break;
			}
			case AmiCenterGraphNode.TYPE_INDEX: {
				style = STYLE_INDEX;
				break;
			}
			case AmiCenterGraphNode.TYPE_DBO: {
				style = STYLE_DBO;
				break;
			}
			case AmiCenterGraphNode.TYPE_METHOD:
				style = STYLE_METHOD;
				break;
			default:
				style = "";
		}
		//TODO:ADD undefined icon 
		//		if (i.getInner() == null && i.getType() != AmiWebGraphNode.TYPE_FEED)
		//			name += "<BR><span style='background:red;color:white;pointer-events:none'>Not Defined</span>";
		int x = Math.max(minx, minXPos[y]);
		minXPos[y] = x + X_SPACING;
		Node node = this.graph.addNode(LEFT_PADDING + x, TOP_PADDING + (this.maxDepth - y) * Y_SPACING, NODE_WIDTH, NODE_HEIGHT, name, style);
		node.setData(i);
		graphNodes.put(i.getUid(), node);
		return node;
	}

	//NOT SURE how walkNodes() and determineDepth() works, TODO: 
	private int determineDepth(AmiCenterGraphNode i) {
		Integer r = null;
		if (r == null) {
			switch (i.getType()) {
				case AmiCenterGraphNode.TYPE_INDEX:
					r = 0;
					break;
				case AmiCenterGraphNode.TYPE_TRIGGER:
					r = 2;
					break;
				case AmiCenterGraphNode.TYPE_TABLE:
					r = 1;
					break;
				default://all other standalone objects will have depth of 0(timer,procedure,method,dbo)
					r = 0;
			}
			depths.put(i.getUid(), r);
		}
		return r;

	}

	private void walkNodes(AmiCenterGraphNode t2, IdentityHashMap<AmiCenterGraphNode, Byte> nodes, byte walkMask) {
		if (t2 == null)
			return;
		Byte existing = nodes.get(t2);
		if (existing == null) {
			existing = 0;
			nodes.put(t2, walkMask);
		} else {
			nodes.put(t2, (byte) (walkMask | existing));
		}
		byte remaining = (byte) (walkMask & (~existing));
		if (remaining == 0)
			return;
		//		nodes.add(t2);
		final boolean walkSource = MH.allBits(remaining, WALK_SOURCE);
		final boolean walkTarget = MH.allBits(remaining, WALK_TARGET);
		//walkNodes(,nodes,1);
		String style = null;
		switch (t2.getType()) {
			case AmiCenterGraphNode.TYPE_TABLE:
				AmiCenterGraphNode_Table tableNode = (AmiCenterGraphNode_Table) t2;
				if (walkTarget) {
					for (AmiCenterGraphNode_Trigger triggerNode : tableNode.getTargetTriggers().values())
						walkNodes(triggerNode, nodes, WALK_TARGET);
					for (AmiCenterGraphNode_Index indexNode : tableNode.getTargetIndexes().values())
						walkNodes(indexNode, nodes, WALK_TARGET);
				}
				style = STYLE_TABLE;
				break;
			case AmiCenterGraphNode.TYPE_TIMER: {
				style = STYLE_TIMER;
				break;
			}
			case AmiCenterGraphNode.TYPE_TRIGGER: {
				AmiCenterGraphNode_Trigger triggerNode = (AmiCenterGraphNode_Trigger) t2;
				if (walkSource) {
					for (AmiCenterGraphNode_Table i : triggerNode.getBindingTables())
						walkNodes(i, nodes, WALK_SOURCE);
				}
				style = STYLE_TRIGGER;
				break;
			}
			case AmiCenterGraphNode.TYPE_PROCEDURE: {
				style = STYLE_PROCEDURE;
				break;
			}
			case AmiCenterGraphNode.TYPE_INDEX: {
				AmiCenterGraphNode_Index indexNode = (AmiCenterGraphNode_Index) t2;
				if (walkSource)
					walkNodes(indexNode.getBindingTable(), nodes, WALK_SOURCE);

				style = STYLE_INDEX;
				break;
			}
			case AmiCenterGraphNode.TYPE_DBO: {
				style = STYLE_DBO;
				break;
			}
			case AmiCenterGraphNode.TYPE_METHOD: {
				style = STYLE_METHOD;
				break;
			}
		}
	}
	private Node addNode(int minx, AmiWebGraphNode i) {
		if (!this.remaining.remove(i))
			return null;
		int y = depths.get(i.getUid());
		String style;
		String name = i.getLabel();
		String description = i.getDescription();
		if (SH.is(description))
			name = name + "<BR>(" + description + ")";
		style = null;
		//		switch (i.getType()) {
		//			case AmiWebGraphNode.TYPE_DATAMODEL:
		//				AmiWebGraphNode_Datamodel dm = (AmiWebGraphNode_Datamodel) i;
		//				if (dm.getSourceDatamodels().isEmpty())
		//					style = STYLE_DM;
		//				else
		//					style = STYLE_BL;
		//				break;
		//			case AmiWebGraphNode.TYPE_DATASOURCE: {
		//				style = STYLE_DS;
		//				break;
		//			}
		//			case AmiWebGraphNode.TYPE_FEED: {
		//				style = STYLE_FD;
		//				break;
		//			}
		//			case AmiWebGraphNode.TYPE_PROCESSOR: {
		//				style = STYLE_RT_PR;
		//				break;
		//			}
		//			case AmiWebGraphNode.TYPE_PANEL: {
		//				AmiWebGraphNode_Panel pn = (AmiWebGraphNode_Panel) i;
		//				if (pn.isRealtime()) {
		//					if (pn.getInner() instanceof AmiWebTreemapPortlet)
		//						style = STYLE_RT_HM;
		//					else if (pn.getInner() instanceof AmiWebAbstractTablePortlet)
		//						style = STYLE_RT_TP;
		//					else if (pn.getInner() instanceof AmiWebTreePortlet)
		//						style = STYLE_RT_TR;
		//					else
		//						style = STYLE_RT_PN;
		//				} else {
		//					if (pn.getInner() instanceof AmiWebQueryFormPortlet)
		//						style = STYLE_ST_FR;
		//					else if (pn.getInner() instanceof AmiWebFilterPortlet)
		//						style = STYLE_ST_FP;
		//					else if (pn.getInner() instanceof AmiWebTreePortlet)
		//						style = STYLE_ST_TR;
		//					else if (pn.getInner() instanceof AmiWebAbstractTablePortlet)
		//						style = STYLE_ST_TP;
		//					else if (pn.getInner() instanceof AmiWebTreemapPortlet)
		//						style = STYLE_ST_HM;
		//					else if (pn.getInner() instanceof AmiWebChartGridPortlet)
		//						style = STYLE_ST_CT;
		//					else if (pn.getInner() instanceof AmiWebTabPortlet)
		//						style = STYLE_ST_TBS;
		//					else
		//						style = STYLE_ST_PN;
		//				}
		//				break;
		//			}
		//			default:
		//				style = "";
		//		}
		if (i.getInner() == null && i.getType() != AmiWebGraphNode.TYPE_FEED)
			name += "<BR><span style='background:red;color:white;pointer-events:none'>Not Defined</span>";
		int x = Math.max(minx, minXPos[y]);
		minXPos[y] = x + X_SPACING;
		Node node = this.graph.addNode(LEFT_PADDING + x, TOP_PADDING + (this.maxDepth - y) * Y_SPACING, NODE_WIDTH, NODE_HEIGHT, name, style);
		node.setData(i);
		graphNodes.put(i.getUid(), node);
		return node;
	}
	@Override
	public void onMenuItem(String id) {
		IterableAndSize<Node> selected = this.graph.getSelectedNodes();
		List<AmiCenterGraphNode> nodes = new ArrayList<AmiCenterGraphNode>(selected.size());
		for (Node i : selected)
			nodes.add((AmiCenterGraphNode) i.getData());
		AmiCenterManagerSmartGraphMenu.onMenuItemAddAction(service, id);
		if (id.startsWith("edit")) {

		}
	}

	@Override
	public void onMenuDismissed() {
		// TODO Auto-generated method stub

	}

	@Override
	public WebMenu createMenu(GraphPortlet graph) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onSelectionChanged(GraphPortlet graphPortlet) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onContextMenu(GraphPortlet graphPortlet, String action) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onUserClick(GraphPortlet graphPortlet, Node nodeOrNull, int button, boolean ctrl, boolean shft) {
		if (button != 2)
			return;
		IterableAndSize<Node> nodes = this.graph.getSelectedNodes();
		List<AmiCenterGraphNode> nodes2 = new ArrayList<AmiCenterGraphNode>(nodes.size());
		if (nodes.size() >= 1)
			CH.first(nodes).setSelected(true);
		for (Node data : nodes) {
			AmiCenterGraphNode n = (AmiCenterGraphNode) data.getData();
			if (n == null)
				return;
			nodes2.add(n);
		}
		BasicWebMenu menu = AmiCenterManagerSmartGraphMenu.createContextMenu(this.service, nodes2, this.allowModification);
		if (menu != null)
			service.getPortletManager().showContextMenu(menu, this);
	}

	@Override
	public void onUserDblClick(GraphPortlet graphPortlet, Integer id) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onKeyDown(String keyCode, String ctrl) {
		// TODO Auto-generated method stub

	}

}
