package com.f1.ami.web.centermanager.portlets;

import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerAbstractEditCenterObjectPortlet;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerEditColumnPortlet;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.DividerPortlet;
import com.f1.suite.web.portal.impl.FastTreePortlet;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.tree.WebTreeNode;
import com.f1.suite.web.tree.impl.FastWebTree;

public class AmiCenterManagerReviewApplyScriptPortlet extends GridPortlet {
	final private FastTreePortlet stepTree;
	final private WebTreeNode reviewSqlNode;
	final private WebTreeNode applySqlNode;
	final private GridPortlet panelGrid;
	final private InnerPortlet reviewOrApplyPanel;
	final private DividerPortlet div;
	final private AmiCenterManagerReviewScriptPortlet reviewPortlet;
	final private AmiCenterManagerApplyScriptPortlet applyPortlet;
	final private AmiCenterManagerAbstractEditCenterObjectPortlet editedObject;

	public AmiCenterManagerReviewApplyScriptPortlet(PortletConfig config, AmiCenterManagerAbstractEditCenterObjectPortlet editedObject, String sql) {
		super(config);
		this.editedObject = editedObject;
		stepTree = new FastTreePortlet(generateConfig());
		stepTree.setFormStyle(AmiWebUtils.getService(getManager()).getUserFormStyleManager());
		stepTree.getTree().setRootLevelVisible(false);
		stepTree.getTree().setSelectionMode(FastWebTree.SELECTION_MODE_NONE);
		//TODO: add FastTree.hideSearch() js, disallow the user to select node
		reviewSqlNode = createNode(stepTree.getRoot(), "Review SQL Script", null, null);
		applySqlNode = createNode(stepTree.getRoot(), "Apply SQL Script", null, null);
		reviewSqlNode.setSelected(true);
		reviewPortlet = new AmiCenterManagerReviewScriptPortlet(generateConfig(), this);
		reviewPortlet.setSql(sql);
		applyPortlet = new AmiCenterManagerApplyScriptPortlet(generateConfig(), this, editedObject);

		panelGrid = new GridPortlet(generateConfig());
		reviewOrApplyPanel = panelGrid.addChild(reviewPortlet, 0, 0, 1, 1);
		reviewOrApplyPanel.setPortlet(reviewPortlet);
		getManager().onPortletAdded(reviewPortlet);
		getManager().onPortletAdded(applyPortlet);

		div = new DividerPortlet(generateConfig(), true, stepTree, panelGrid);
		div.setOffsetFromTopPx(205);
		addChild(div);
	}

	private WebTreeNode createNode(WebTreeNode parent, String title, String icon, Object data) {
		WebTreeNode r = stepTree.createNode(title, parent, false, data);
		r.setIconCssStyle(icon == null ? null : "_bgi=url('" + icon + "')");
		return r;
	}

	public void moveToApplyStage() {
		reviewOrApplyPanel.setPortlet(applyPortlet);
		reviewSqlNode.setSelected(false);
		applySqlNode.setSelected(true);
	}

	public AmiCenterManagerApplyScriptPortlet getApplyPortlet() {
		return applyPortlet;
	}

	public void backToReviewStage() {
		reviewOrApplyPanel.setPortlet(reviewPortlet);
		reviewSqlNode.setSelected(true);
		applySqlNode.setSelected(false);
	}

}
