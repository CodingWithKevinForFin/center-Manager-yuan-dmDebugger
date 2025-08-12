package com.f1.ami.web.centermanager.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.graph.AmiWebCenterGraphManager;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Table;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Trigger;
import com.f1.ami.web.centermanager.portlets.AmiWebCenterManagerPortlet;
import com.f1.ami.web.graph.AmiWebGraphNode_Link;
import com.f1.base.IterableAndSize;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenuLink;
import com.f1.suite.web.portal.impl.WebMenuListener;
import com.f1.suite.web.portal.impl.visual.GraphContextMenuFactory;
import com.f1.suite.web.portal.impl.visual.GraphListener;
import com.f1.suite.web.portal.impl.visual.GraphPortlet;
import com.f1.suite.web.portal.impl.visual.GraphPortlet.Edge;
import com.f1.suite.web.portal.impl.visual.GraphPortlet.Node;
import com.f1.utils.CH;
import com.f1.utils.MH;
import com.f1.utils.OH;
import com.f1.utils.SH;
import com.f1.utils.concurrent.IdentityHashSet;
import com.f1.utils.structs.LongKeyMap;

//this class only shows relationship between tables and triggers
public class AmiCenterManagerEntityRelationGraph implements GraphListener, GraphContextMenuFactory, WebMenuListener {
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

	public static final int MAX_GRAPH_DEPTH = 4;

	private GraphPortlet erGraph;
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
	//For walking nodes for a table
	private static byte WALK_INBOUND_TRIGGER = 1;
	private static byte WALK_OUTBOUND_TRIGGER = 2;
	private static byte WALK_INBOUND_TRIGGER_SOURCE = 4;
	private static byte WALK_INBOUND_TRIGGER_TARGET = 8;
	private static byte WALK_OUTBOUND_TRIGGER_SOURCE = 16;
	private static byte WALK_OUTBOUND_TRIGGER_TARGET = 32;

	//For walking nodes for a trigger
	private static byte WALK_TRIGGER_SOURCE_TABLE = 1;
	private static byte WALK_TRIGGER_TARGET_TABLE = 2;
	private boolean referenceDepthSet = false;

	private final Comparator<AmiCenterGraphNode> comparator = new Comparator<AmiCenterGraphNode>() {

		@Override
		public int compare(AmiCenterGraphNode o1, AmiCenterGraphNode o2) {
			Integer d1 = depths.get(o1.getUid());
			Integer d2 = depths.get(o2.getUid());
			int n = OH.compare(d1, d2, true);
			return n == 0 ? AmiWebCenterGraphManager.COMPARATOR_ID.compare(o1, o2) : n;
		}
	};

	public AmiCenterManagerEntityRelationGraph(AmiWebCenterManagerPortlet owner, AmiWebService service, GraphPortlet graph, boolean allowModification) {
		this.erGraph = graph;
		this.owner = owner;
		this.allowModification = allowModification;
		this.erGraph.setMenuFactory(this);
		this.service = service;
		this.erGraph.addGraphListener(this);
		this.erGraph.setSnapSize(5);
		this.erGraph.setGridSize(20);
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
	/* General Rules: <br>
	 * Rule0: Only table and trigger nodes will be considered in the graph. <br>
	 * Rule1: If only one table node is selected, graph will only be built up to the depth of 4(max 4) <br>
	 * Rule2: If multiple table/trigger nodes are selected, the graph will be built/merged based on these tables/triggers<br>
	 * 
	 * Linking Rules: <br>
	 * Rule0: For join trigger, left table will point to right table
	 */
	public void rebuild() {
		//		IdentityHashMap<AmiCenterGraphNode, Byte> allNodes = new IdentityHashMap<AmiCenterGraphNode, Byte>();
		//		List<AmiCenterGraphNode_Table> origTables = new ArrayList<AmiCenterGraphNode_Table>();
		//		List<AmiCenterGraphNode_Trigger> origTriggers = new ArrayList<AmiCenterGraphNode_Trigger>();
		//
		//		for (AmiCenterGraphNode n : this.origNodes) {
		//			if (n instanceof AmiCenterGraphNode_Table) {
		//				AmiCenterGraphNode_Table table = (AmiCenterGraphNode_Table) n;
		//				table.setPrimaryNode(true);
		//				origTables.add(table);
		//			} else if (n instanceof AmiCenterGraphNode_Trigger) {
		//				AmiCenterGraphNode_Trigger trigger = (AmiCenterGraphNode_Trigger) n;
		//				//tri.setPrimaryNode(true);
		//				origTriggers.add(trigger);
		//			}
		//		}
		//
		//		boolean isOneNodeSelected = this.origNodes.size() == 1;
		//		boolean isOneNodeTrigger = isOneNodeSelected && this.origNodes.iterator().next() instanceof AmiCenterGraphNode_Trigger;
		//		boolean isOneNodeTable = isOneNodeSelected && this.origNodes.iterator().next() instanceof AmiCenterGraphNode_Table;
		//		AmiCenterGraphNode_Table primaryTable = null;
		//		if (isOneNodeTable) {
		//			primaryTable = ((AmiCenterGraphNode_Table) this.origNodes.iterator().next());
		//			primaryTable.setPrimaryNode(true);
		//		}
		//
		//		for (AmiCenterGraphNode i : this.origNodes)
		//			walkNodes(i, allNodes, (byte) (WALK_INBOUND_TRIGGER | WALK_OUTBOUND_TRIGGER | WALK_INBOUND_TRIGGER_SOURCE | WALK_INBOUND_TRIGGER_TARGET | WALK_OUTBOUND_TRIGGER_SOURCE
		//					| WALK_OUTBOUND_TRIGGER_TARGET));
		//
		//		this.allNodes.clear();
		//		for (AmiCenterGraphNode i : allNodes.keySet())
		//			this.allNodes.put(i.getUid(), i);
		//		this.graphNodes.clear();
		//		this.erGraph.clear();
		//		this.depths.clear();
		//		this.remaining.clear();
		//		for (AmiCenterGraphNode i : this.allNodes.values())
		//			this.remaining.add(i);
		//
		//		int depth = 0;
		//
		//		List<AmiCenterGraphNode_Table> tables = new ArrayList<AmiCenterGraphNode_Table>();
		//		List<AmiCenterGraphNode_Trigger> triggers = new ArrayList<AmiCenterGraphNode_Trigger>();
		//		//First determine the depth for the origNodes
		//
		//		int primaryNodeDepth = determineDepth(this.origNodes.iterator().next());
		//		for (AmiCenterGraphNode i : this.allNodes.values()) {
		//			depth = Math.max(depth, determineDepth(i));
		//			if (i.getType() == AmiCenterGraphNode.TYPE_TABLE)
		//				tables.add((AmiCenterGraphNode_Table) i);
		//			else if (i.getType() == AmiCenterGraphNode.TYPE_TRIGGER)
		//				triggers.add((AmiCenterGraphNode_Trigger) i);
		//		}
		//
		//		this.maxDepth = depth;
		//		minXPos = new int[depth + 1];
		//		//Start with tables
		//		for (AmiCenterGraphNode_Table i : sort(tables)) {
		//			if (i.getTargetIndexes().isEmpty() && i.getTargetTriggers().isEmpty())
		//				continue;
		//			Node node = addNode(0, i);
		//			if (node == null)
		//				continue;
		//			List<Node> childs = new ArrayList<Node>();
		//			for (AmiCenterGraphNode_Trigger j : sort(i.getOutboundTriggers()))
		//				CH.addSkipNull(childs, addToGraph(getX(node), j));
		//			center(node, childs);
		//		}
		//
		//		//then for tables without indexes or triggers
		//		for (AmiCenterGraphNode i : CH.l(remaining)) {
		//			addNode(0, i);
		//		}
		//
		//		//build links, start with primary tables
		//		//		for(AmiCenterGraphNode_Table primaryTable: origTables) {
		//		//			
		//		//		}
		//
		//		//start with inbound
		//		for (AmiCenterGraphNode_Trigger inboundTrigger : primaryTable.getInboundTriggers()) {
		//			Node sourceTrigger = graphNodes.get(inboundTrigger.getUid());
		//			Node targetTable = graphNodes.get(primaryTable.getUid());
		//			addDataEdge(sourceTrigger, targetTable);
		//			if (inboundTrigger.getTriggerType() == AmiCenterEntityConsts.TRIGGER_TYPE_CODE_JOIN)
		//				addSourceTableLinkForJoinTrigger(inboundTrigger);
		//			for (AmiCenterGraphNode_Table t : inboundTrigger.getSourceTables()) {
		//				Node sourceTableNode = graphNodes.get(t.getUid());
		//				addDataEdge(sourceTableNode, sourceTrigger);
		//			}
		//		}
		//
		//		//then do outbound
		//		for (AmiCenterGraphNode_Trigger outboundTrigger : primaryTable.getOutboundTriggers()) {
		//			Node sourceTable = graphNodes.get(primaryTable.getUid());
		//			Node targetTrigger = graphNodes.get(outboundTrigger.getUid());
		//			addDataEdge(sourceTable, targetTrigger);
		//			if (outboundTrigger.getTriggerType() == AmiCenterEntityConsts.TRIGGER_TYPE_CODE_JOIN)
		//				addSourceTableLinkForJoinTrigger(outboundTrigger);
		//			for (AmiCenterGraphNode_Table t : outboundTrigger.getSinkTables()) {
		//				Node sourceTriggerNode = graphNodes.get(outboundTrigger.getUid());
		//				Node targetTable = graphNodes.get(t.getUid());
		//				addDataEdge(sourceTriggerNode, targetTable);
		//			}
		//		}

		SetMerger<AmiCenterGraphNode> m = new SetMerger<AmiCenterGraphNode>();
		List<IdentityHashSet<AmiCenterGraphNode>> sets = new ArrayList<IdentityHashSet<AmiCenterGraphNode>>();
		for (AmiCenterGraphNode i : this.origNodes) {
			IdentityHashSet<AmiCenterGraphNode> groupNodes = new IdentityHashSet<AmiCenterGraphNode>();
			IdentityHashMap<AmiCenterGraphNode, Byte> allNodes = new IdentityHashMap<AmiCenterGraphNode, Byte>();
			//1.walk all participant nodes for node i
			walkNodes(i, allNodes, (byte) (WALK_INBOUND_TRIGGER | WALK_OUTBOUND_TRIGGER | WALK_INBOUND_TRIGGER_SOURCE | WALK_INBOUND_TRIGGER_TARGET | WALK_OUTBOUND_TRIGGER_SOURCE
					| WALK_OUTBOUND_TRIGGER_TARGET));
			groupNodes.addAll(allNodes.keySet());
			sets.add(groupNodes);
		}

		this.allNodes.clear();
		//		for (AmiCenterGraphNode i : allNodes.keySet())
		//			this.allNodes.put(i.getUid(), i);
		this.graphNodes.clear();
		this.erGraph.clear();
		this.depths.clear();
		this.remaining.clear();
		for (AmiCenterGraphNode i : this.allNodes.values())
			this.remaining.add(i);

		//2. merge sets
		List<Set<AmiCenterGraphNode>> mergedGroupSets = m.mergeSets(sets);
		//		System.out.println(m.uf.numComponents);
		//		System.out.println(mergedGroupSets);

		//3.determine depth
		for (Set<AmiCenterGraphNode> groupNode : mergedGroupSets) {
			//set once per groupNode
			referenceDepthSet = false;
			LongKeyMap<Integer> cache = new LongKeyMap<Integer>();
			for (AmiCenterGraphNode i : groupNode) {
				if (!(i instanceof AmiCenterGraphNode_Table || i instanceof AmiCenterGraphNode_Trigger))
					continue;
				int depth = determineDepth(i, null, groupNode);
				cache.put(i.getUid(), depth);
				this.allNodes.put(i.getUid(), i);
				this.remaining.add(i);

			}
			//normalize depth to 0-max
			normalizeDepth(cache);
		}

		Integer[] normalizedDepths = depths.getValues(new Integer[depths.size()]);
		Integer maxDepth = null;
		if (normalizedDepths.length > 0)
			maxDepth = Collections.max(Arrays.asList(normalizedDepths));
		else
			maxDepth = -1;
		this.maxDepth = maxDepth;
		minXPos = new int[maxDepth + 1];

		//grouup triggers and tables
		List<AmiCenterGraphNode_Table> tables = new ArrayList<AmiCenterGraphNode_Table>();
		List<AmiCenterGraphNode_Trigger> triggers = new ArrayList<AmiCenterGraphNode_Trigger>();
		for (AmiCenterGraphNode i : this.allNodes.values()) {
			if (i.getType() == AmiCenterGraphNode.TYPE_TABLE)
				tables.add((AmiCenterGraphNode_Table) i);
			else if (i.getType() == AmiCenterGraphNode.TYPE_TRIGGER)
				triggers.add((AmiCenterGraphNode_Trigger) i);
		}

		//Start with tables
		for (AmiCenterGraphNode_Table i : sort(tables)) {
			//			if (i.getTargetIndexes().isEmpty() && i.getTargetTriggers().isEmpty())
			//				continue;
			Node node = addNode(0, i);
			if (node == null)
				continue;
			List<Node> childs = new ArrayList<Node>();
			for (AmiCenterGraphNode_Trigger j : sort(i.getOutboundTriggers()))
				CH.addSkipNull(childs, addToGraph(getX(node), j));
			center(node, childs);
		}

		//build links
		//start with inbound
		for (AmiCenterGraphNode_Table primaryTable : tables) {
			for (AmiCenterGraphNode_Trigger inboundTrigger : primaryTable.getInboundTriggers()) {
				Node targetTable = graphNodes.get(primaryTable.getUid());
				if (this.allNodes.containsKey(inboundTrigger.getUid())) {
					Node sourceTrigger = graphNodes.get(inboundTrigger.getUid());
					if (!hasEdge(sourceTrigger, targetTable))
						addDataEdge(sourceTrigger, targetTable);
					if (inboundTrigger.getTriggerType() == AmiCenterEntityConsts.TRIGGER_TYPE_CODE_JOIN)
						addSourceTableLinkForJoinTrigger(inboundTrigger);
					for (AmiCenterGraphNode_Table t : inboundTrigger.getSourceTables()) {
						Node sourceTableNode = graphNodes.get(t.getUid());
						if (!hasEdge(sourceTableNode, sourceTrigger))
							addDataEdge(sourceTableNode, sourceTrigger);
					}
				}

			}

			//then do outbound
			for (AmiCenterGraphNode_Trigger outboundTrigger : primaryTable.getOutboundTriggers()) {
				Node sourceTable = graphNodes.get(primaryTable.getUid());
				if (this.allNodes.containsKey(outboundTrigger.getUid())) {
					Node targetTrigger = graphNodes.get(outboundTrigger.getUid());
					if (!hasEdge(sourceTable, targetTrigger))
						addDataEdge(sourceTable, targetTrigger);
					if (outboundTrigger.getTriggerType() == AmiCenterEntityConsts.TRIGGER_TYPE_CODE_JOIN)
						addSourceTableLinkForJoinTrigger(outboundTrigger);
					for (AmiCenterGraphNode_Table t : outboundTrigger.getSinkTables()) {
						Node sourceTriggerNode = graphNodes.get(outboundTrigger.getUid());
						Node targetTable = graphNodes.get(t.getUid());
						if (!hasEdge(sourceTriggerNode, targetTable))
							addDataEdge(sourceTriggerNode, targetTable);
					}
				}

			}
		}

	}

	public void addSourceTableLinkForJoinTrigger(AmiCenterGraphNode_Trigger node) {
		Set<AmiCenterGraphNode_Table> srcs = node.getSourceTables();
		Iterator<AmiCenterGraphNode_Table> i = srcs.iterator();
		AmiCenterGraphNode_Table left = i.next();
		AmiCenterGraphNode_Table right = i.next();
		Node sourceNode = graphNodes.get(left.getUid());
		Node targetNode = graphNodes.get(right.getUid());
		if (!hasEdge(sourceNode, targetNode))
			addDataEdge(sourceNode, targetNode);

	}

	private int getX(Node node) {
		return node.getX() - LEFT_PADDING;
	}

	//responsible for adding the trigger's tables to the graph
	private Node addToGraph(int minx, AmiCenterGraphNode_Trigger trigger) {
		Node node = addNode(minx, trigger);
		if (node == null)
			return null;
		minx = getX(node);
		List<Node> srcChilds = new ArrayList<Node>();
		//		for (AmiCenterGraphNode_Table j : trigger.getSinkTables())
		//			CH.addSkipNull(srcChilds, addToGraph(minx, j));
		center(node, srcChilds);
		return node;
	}

	private void center(Node node, List<Node> childs) {
		if (childs.isEmpty())
			return;
		int min = getX(childs.get(0));
		int max = min;
		for (int i = 1; i < childs.size(); i++) {
			int t = getX(childs.get(i));
			min = Math.min(min, t);
			max = Math.max(max, t);
		}
		int mid = (max + min) / 2;
		if (mid > getX(node)) {
			AmiCenterGraphNode n = (AmiCenterGraphNode) node.getData();
			node.setX(LEFT_PADDING + mid);
			minXPos[depths.get(n.getUid())] = getX(node) + X_SPACING;
		}

	}

	private Node addNode(int minx, AmiCenterGraphNode i) {
		if (!this.remaining.remove(i))
			return null;
		int y = depths.get(i.getUid());
		String style;
		String name = i.getLabel();
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
		int x = Math.max(minx, minXPos[y]);
		minXPos[y] = x + X_SPACING;
		Node node = this.erGraph.addNode(LEFT_PADDING + x, TOP_PADDING + (this.maxDepth - y) * Y_SPACING, NODE_WIDTH, NODE_HEIGHT, name, style);
		node.setData(i);
		graphNodes.put(i.getUid(), node);
		return node;
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

	private void addDataEdge(Node sourceNode, Node targetNode) {
		Edge edge = this.erGraph.addEdge(sourceNode.getId(), targetNode.getId());
		edge.setColor("#AEAEAE");
		edge.setDirection(GraphPortlet.DIRECTION_FORWARD);
	}

	private boolean hasEdge(Node sourceNode, Node targetNode) {
		Iterable<Edge> existingEdges = this.erGraph.getEdges();
		for (Edge e : existingEdges) {
			if (e.getDirection() == GraphPortlet.DIRECTION_FORWARD && e.getNodeId1() == sourceNode.getId() && e.getNodeId2() == targetNode.getId())
				return true;
		}
		return false;
	}

	//normalize depths to 0->max
	private void normalizeDepth(LongKeyMap<Integer> orig) {
		Integer[] unnormalizedDepths = orig.getValues(new Integer[orig.size()]);
		if (unnormalizedDepths.length == 0)
			return;
		Integer minDepth = Collections.min(Arrays.asList(unnormalizedDepths));
		Integer maxDepth = Collections.max(Arrays.asList(unnormalizedDepths));
		int diff = 0 - minDepth;
		if (diff == 0)
			return;
		//add to diff for each value, normalize_val = orig  + diff
		for (Long key : orig.getKeys()) {
			depths.put(key, depths.get(key) + diff);
		}
	}

	private int determineDepth(AmiCenterGraphNode i, Integer depthToSet, Set<AmiCenterGraphNode> visibleNodes) {
		Integer r = depths.get(i.getUid());
		if (r == null) {
			switch (i.getType()) {
				case AmiCenterGraphNode.TYPE_TRIGGER:
					//determine own depth
					if (!referenceDepthSet) {//init the depth to 0(reference)
						referenceDepthSet = true;
						depths.put(i.getUid(), 0);
					} else {
						OH.assertNotNull(depthToSet);
						depths.put(i.getUid(), depthToSet);
					}
					//determine child depth
					AmiCenterGraphNode_Trigger j = (AmiCenterGraphNode_Trigger) i;
					boolean hasSourceTable = !j.getSourceTables().isEmpty();
					boolean hasSinkTable = !j.getSinkTables().isEmpty();
					if (hasSourceTable) {
						//ownDepth - 1
						int sourceTableLevel = depths.get(i.getUid()) - 1;
						for (AmiCenterGraphNode_Table tt : j.getSourceTables()) {
							if (visibleNodes.contains(tt) && depths.get(tt.getUid()) == null) {
								determineDepth(tt, sourceTableLevel, visibleNodes);
							}
						}
					}
					if (hasSinkTable) {
						//curDepth + 1
						int sinkTableLevel = depths.get(i.getUid()) + 1;
						for (AmiCenterGraphNode_Table tt : j.getSinkTables()) {
							if (visibleNodes.contains(tt) && depths.get(tt.getUid()) == null) {
								determineDepth(tt, sinkTableLevel, visibleNodes);
							}
						}
					}
					break;
				case AmiCenterGraphNode.TYPE_TABLE:
					if (!referenceDepthSet) {//init the depth to 0(reference)
						referenceDepthSet = true;
						depths.put(i.getUid(), 0);
					} else {
						OH.assertNotNull(depthToSet);
						depths.put(i.getUid(), depthToSet);
					}
					AmiCenterGraphNode_Table t = (AmiCenterGraphNode_Table) i;
					boolean hasOutbound = !t.getOutboundTriggers().isEmpty();
					boolean hasInbound = !t.getInboundTriggers().isEmpty();
					//first check inbound trigger
					if (hasInbound) {
						//curDepth - 1
						int inboundTriggerLevel = depths.get(i.getUid()) - 1;
						for (AmiCenterGraphNode_Trigger tr : t.getInboundTriggers()) {
							if (visibleNodes.contains(tr) && depths.get(tr.getUid()) == null) {
								//								depths.put(tr.getUid(), inboundTriggerLevel);
								determineDepth(tr, inboundTriggerLevel, visibleNodes);
								for (AmiCenterGraphNode_Table tt : tr.getSourceTables()) {
									if (visibleNodes.contains(tt) && depths.get(tt.getUid()) == null)
										//depths.put(tt.getUid(), inboundTriggerLevel - 1);
										determineDepth(tt, inboundTriggerLevel - 1, visibleNodes);
								}
							}
						}
					}
					if (hasOutbound) {
						//curDepth + 1
						int outboundTriggerLevel = depths.get(i.getUid()) + 1;
						for (AmiCenterGraphNode_Trigger tr : t.getOutboundTriggers()) {
							if (visibleNodes.contains(tr) && depths.get(tr.getUid()) == null) {
								//depths.put(tr.getUid(), outboundTriggerLevel);
								determineDepth(tr, outboundTriggerLevel, visibleNodes);
								for (AmiCenterGraphNode_Table tt : tr.getSinkTables()) {
									if (visibleNodes.contains(tt) && depths.get(tt.getUid()) == null) {
										//depths.put(tt.getUid(), outboundTriggerLevel + 1);
										determineDepth(tt, outboundTriggerLevel + 1, visibleNodes);
									}
								}
								for (AmiCenterGraphNode_Table tt : tr.getSourceTables()) {
									if (tt == i)
										continue;
									if (visibleNodes.contains(tt) && depths.get(tt.getUid()) == null) {
										//depths.put(tt.getUid(), depths.get(i.getUid()));
										determineDepth(tt, depthToSet, visibleNodes);

									}
								}
							}

						}
					}

					break;
				default:
					break;
				//					throw new IllegalArgumentException("only support building graphs for tables and triggers");
			}

		}
		//System.out.println(depths);
		OH.assertTrue(depths.get(i.getUid()) != null);
		return depths.get(i.getUid());

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
		final boolean walkInboundTrigger = MH.allBits(remaining, WALK_INBOUND_TRIGGER);
		final boolean walkOutboundTrigger = MH.allBits(remaining, WALK_OUTBOUND_TRIGGER);
		final boolean walkInboundTriggerSource = MH.allBits(remaining, WALK_INBOUND_TRIGGER_SOURCE);
		final boolean walkInboundTriggerTarget = MH.allBits(remaining, WALK_INBOUND_TRIGGER_TARGET);
		final boolean walkOutboundTriggerSource = MH.allBits(remaining, WALK_OUTBOUND_TRIGGER_SOURCE);
		final boolean walkOutboundTriggerTarget = MH.allBits(remaining, WALK_OUTBOUND_TRIGGER_TARGET);
		//walkNodes(,nodes,1);
		String style = null;
		switch (t2.getType()) {
			case AmiCenterGraphNode.TYPE_TABLE:
				AmiCenterGraphNode_Table tableNode = (AmiCenterGraphNode_Table) t2;
				if (walkOutboundTrigger) {//outbound
					for (AmiCenterGraphNode_Trigger triggerNode : tableNode.getOutboundTriggers())
						walkNodes(triggerNode, nodes, (byte) (WALK_OUTBOUND_TRIGGER_SOURCE | WALK_OUTBOUND_TRIGGER_TARGET));
				}
				if (walkInboundTrigger) {//inbound
					for (AmiCenterGraphNode_Trigger triggerNode : tableNode.getInboundTriggers())
						walkNodes(triggerNode, nodes, (byte) (WALK_INBOUND_TRIGGER_SOURCE | WALK_INBOUND_TRIGGER_TARGET));
				}
				//				if (walkInboundTriggerSource || walkInboundTriggerTarget || walkOutboundTriggerSource || walkOutboundTriggerTarget)
				//					System.out.println("Will only walk up to trigger source/target tables");
				style = STYLE_TABLE;
				break;
			case AmiCenterGraphNode.TYPE_TRIGGER: {
				AmiCenterGraphNode_Trigger triggerNode = (AmiCenterGraphNode_Trigger) t2;
				if (walkInboundTriggerSource) {
					for (AmiCenterGraphNode_Table i : triggerNode.getSourceTables())
						walkNodes(i, nodes, WALK_INBOUND_TRIGGER_SOURCE);
				}
				if (walkInboundTriggerTarget) {
					for (AmiCenterGraphNode_Table i : triggerNode.getSinkTables())
						walkNodes(i, nodes, WALK_INBOUND_TRIGGER_SOURCE);
				}
				if (walkOutboundTriggerSource) {
					for (AmiCenterGraphNode_Table i : triggerNode.getSourceTables())
						walkNodes(i, nodes, WALK_OUTBOUND_TRIGGER_SOURCE);
				}
				if (walkOutboundTriggerTarget) {
					for (AmiCenterGraphNode_Table i : triggerNode.getSinkTables())
						walkNodes(i, nodes, WALK_OUTBOUND_TRIGGER_SOURCE);
				}
				style = STYLE_TRIGGER;
				break;
			}

		}
	}

	@Override
	public void onMenuItem(String id) {
		IterableAndSize<Node> selected = this.erGraph.getSelectedNodes();
		List<AmiCenterGraphNode> nodes = new ArrayList<AmiCenterGraphNode>(selected.size());
		for (Node i : selected)
			nodes.add((AmiCenterGraphNode) i.getData());
		if ("navigate".equals(id)) {
			this.buildGraph(new IdentityHashSet<AmiCenterGraphNode>(nodes), new IdentityHashSet<AmiCenterGraphNode>(nodes));
		} else if (SH.startsWith(id, "add")) {
			AmiCenterManagerSmartGraphMenu.onMenuItemAddAction(service, id);
		} else if ("edit_table".equals(id)) {
			owner.onContextMenuOnNodes(id, nodes);
		} else if ("edit_trigger".equals(id)) {
			owner.onContextMenuOnNodes(id, nodes);
		} else if ("edit_procedure".equals(id)) {
			owner.onContextMenuOnNodes(id, nodes);
		} else if ("edit_timer".equals(id)) {
			owner.onContextMenuOnNodes(id, nodes);
		} else if ("edit_index".equals(id)) {
			owner.onContextMenuOnNodes(id, nodes);
		} else if ("edit_dbo".equals(id)) {
			owner.onContextMenuOnNodes(id, nodes);
		} else if ("view_index".equals(id)) {
			AmiCenterManagerEntityRelationGraphMenu.viewIndex((AmiCenterGraphNode_Table) nodes.get(0), service.getPortletManager());
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
		IterableAndSize<Node> nodes = getGraph().getSelectedNodes();
		List<AmiCenterGraphNode> nodes2 = new ArrayList<AmiCenterGraphNode>(nodes.size());
		if (nodes.size() >= 1)
			CH.first(nodes).setSelected(true);
		for (Node node : nodes) {
			AmiCenterGraphNode n = (AmiCenterGraphNode) node.getData();
			if (n == null)
				return;
			nodes2.add(n);
		}
		BasicWebMenu menu = AmiCenterManagerEntityRelationGraphMenu.createContextMenu(service, nodes2, this.allowModification);
		if (nodes2.size() > 0) {
			menu.addChild(new BasicWebMenuLink("Navigate To", true, "navigate"));
		}
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

	public GraphPortlet getGraph() {
		return this.erGraph;
	}

	public static class UnionFind {
		public int[] parent;
		public int size;
		public int[] sizes;
		public int numComponents;

		public UnionFind(int n) {
			size = n;
			sizes = new int[n];
			numComponents = n;
			parent = new int[n];
			for (int i = 0; i < n; i++) {
				parent[i] = i;
				sizes[i] = 1;
			}

		}

		boolean isConnected(int p, int q) {
			return find(p) == find(q);
		}

		int find(int x) {
			if (parent[x] != x)
				parent[x] = find(parent[x]); // path compression
			return parent[x];
		}

		void union(int x, int y) {

			int root_x = find(x);
			int root_y = find(y);

			if (root_x == root_y)
				return;
			//merge smaller components into the larger one
			if (sizes[root_x] < sizes[root_y]) {
				sizes[root_y] += sizes[root_x];
				parent[root_x] = parent[root_y];
			} else {
				sizes[root_x] += sizes[root_y];
				parent[root_y] = parent[root_x];
			}
			numComponents--;

		}
	}

	public static class SetMerger<T> {
		UnionFind uf;

		public List<Set<T>> mergeSets(List<IdentityHashSet<T>> sets) {
			int n = sets.size();
			uf = new UnionFind(n);
			Map<T, Integer> itemToSet = new HashMap<>();

			// Union sets that share at least one common element
			for (int i = 0; i < n; i++) {
				for (T item : sets.get(i)) {
					if (itemToSet.containsKey(item)) {
						uf.union(i, itemToSet.get(item));
					} else {
						itemToSet.put(item, i);
					}
				}
			}

			// Merge sets according to union-find roots
			Map<Integer, Set<T>> merged = new HashMap<>();
			for (int i = 0; i < n; i++) {
				int root = uf.find(i);
				merged.computeIfAbsent(root, new Function<Integer, Set<T>>() {
					@Override
					public Set<T> apply(Integer k) {
						return new HashSet<>();
					}
				}).addAll(sets.get(i));
			}

			return new ArrayList<>(merged.values());
		}
	}

}
