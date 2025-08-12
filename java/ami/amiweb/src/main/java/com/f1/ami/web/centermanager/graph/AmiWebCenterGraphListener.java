package com.f1.ami.web.centermanager.graph;

import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;

public interface AmiWebCenterGraphListener {
	void onCenterNodeAdded(AmiCenterGraphNode node);
	void onCenterNodeRemoved(AmiCenterGraphNode node);

}
