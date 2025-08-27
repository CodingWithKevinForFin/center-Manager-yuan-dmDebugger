package com.f1.ami.web.centermanager.editors;

import java.util.HashMap;
import java.util.Map;

import com.f1.ami.portlets.AmiWebHeaderPortlet;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerTriggerScirptTreePortlet;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Index;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Table;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Trigger;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.PortletManager;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.TabPortlet;
import com.f1.utils.SH;
import com.f1.utils.string.sqlnode.CreateTableNode;

public class AmiCenterManagerRichTableEditorPortlet extends GridPortlet {
	private static final String BG_GREY = "_bg=#4c4c4c";
	private static final String TABLE_FORM_FIELD_STYLE = BG_GREY + "|_fm=courier,bold|_fg=#ffffff|_fs=18px|style.border=0px";

	final private AmiWebService service;
	private AmiWebHeaderPortlet header;
	private AmiCenterGraphNode_Table correlationNode;

	private TabPortlet tableEditorTabsPortlet;//contains triggers,indexes,columns

	public AmiCenterManagerRichTableEditorPortlet(PortletConfig config, String tableSql, AmiCenterGraphNode_Table correlationNode, boolean isAdd) {
		super(config);
		this.service = AmiWebUtils.getService(getManager());
		this.correlationNode = correlationNode;
		this.header = new AmiWebHeaderPortlet(generateConfig());
		this.header.updateBlurbPortletLayout("Rich Table Editor", null);
		this.header.setShowSearch(false);
		this.addChild(header, 0, 0, 1, 1);

		PortletManager manager = service.getPortletManager();
		this.tableEditorTabsPortlet = new TabPortlet(generateConfig());
		this.tableEditorTabsPortlet.getTabPortletStyle().setBackgroundColor("#4c4c4c");
		AmiCenterManagerEditColumnPortlet cp = null;
		AmiCenterManagerTriggerScirptTreePortlet tst = null;
		AmiCenterManagerIndexScirptTreePortlet ist = null;

		//parse sql
		if (!isAdd) {
			boolean hasIndex = tableSql.contains("CREATE INDEX");
			String createTableScript = null;
			if (hasIndex)
				createTableScript = SH.beforeFirst(tableSql, "CREATE INDEX");
			else
				createTableScript = tableSql;
			CreateTableNode ctn = AmiCenterManagerUtils.scriptToCreateTableNode(createTableScript);
			Map<String, String> tableConfig = AmiCenterManagerUtils.parseAdminNode_Table(ctn);
			String tableName = tableConfig.get("name");
			//manager.showDialog("Edit Table", new AmiCenterManagerAddTablePortlet(manager.generateConfig(), tableConfig, AmiCenterEntityTypeConsts.EDIT), 500, 550);
			Map<String, AmiCenterGraphNode_Trigger> triggerBinding = this.service.getCenterGraphManager().getTable(tableName).getTargetTriggers();

			Map<String, AmiCenterGraphNode_Index> indexBinding = this.service.getCenterGraphManager().getTable(tableName).getTargetIndexes();

			cp = new AmiCenterManagerEditColumnPortlet(manager.generateConfig(), this, createTableScript, correlationNode);
			tst = new AmiCenterManagerTriggerScirptTreePortlet(manager.generateConfig(), triggerBinding);
			ist = new AmiCenterManagerIndexScirptTreePortlet(manager.generateConfig(), indexBinding, this.tableEditorTabsPortlet);

		} else {
			cp = new AmiCenterManagerEditColumnPortlet(manager.generateConfig(), this, true);
			tst = new AmiCenterManagerTriggerScirptTreePortlet(manager.generateConfig(), new HashMap<String, AmiCenterGraphNode_Trigger>());
			ist = new AmiCenterManagerIndexScirptTreePortlet(manager.generateConfig(), new HashMap<String, AmiCenterGraphNode_Index>(), this.tableEditorTabsPortlet);
		}

		this.tableEditorTabsPortlet.addChild("Table/Columns", cp);
		this.tableEditorTabsPortlet.addChild("Triggers", tst);
		this.tableEditorTabsPortlet.addChild("Indexes", ist);

		this.tableEditorTabsPortlet.setIsCustomizable(false);
		this.addChild(this.tableEditorTabsPortlet, 0, 1, 1, 1);

	}

	public AmiCenterManagerRichTableEditorPortlet(PortletConfig config, boolean isAdd) {
		this(config, null, null, isAdd);
	}

	public AmiCenterGraphNode_Table getCorrelationNode() {
		return this.correlationNode;
	}

	@Override
	public void close() {
		super.close();
		this.service.getAmiCenterManagerEditorsManager().onPortletClosed(this);
	}
}
