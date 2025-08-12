package com.f1.ami.web.centermanager.portlets;

import java.util.Map;

import com.f1.ami.portlets.AmiWebHeaderPortlet;
import com.f1.ami.portlets.AmiWebHeaderSearchHandler;
import com.f1.suite.web.portal.impl.RootPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletField;

/**
 * Table: #32CD32 (light green) Timer: #008080 (Teal) Trigger: #FF8C00 (Dark Orange) Procedure: #8A2BE2 (Blue Violet) Method: #00CED1 (Dark Turquoise) DBO: #FFD700 (Gold)
 * 
 * @author YuanYao
 *
 */
public class AmiWebCenterManagerHeaderPortlet implements AmiWebHeaderSearchHandler {

	private static final String SHOW_USER_DEFINED_ONLY = "      Show User Defined Only";

	public static final String TABLE_OBJECTS_HTML = "<span style=\"color:#32CD32\"> <B>Table objects are green</B></span>";
	public static final String TIMER_OBJECTS_HTML = "<span style=\"color:#008080\"> <B>Timer objects are teal</B></span>";
	public static final String TRIGGER_OBJECTS_HTML = "<span style=\"color:#FF8C00\"> <B>Trigger objects are orange</B></span>";
	public static final String PROCEDURE_OBJECTS_HTML = "<span style=\"color:#8A2BE2\"> <B>Procedure objects are blue</B></span>";
	public static final String METHOD_OBJECTS_HTML = "<span style=\"color:#00CED1\"> <B>Method objects are dark turquoise</B></span>";
	public static final String DBO_OBJECTS_HTML = "<span style=\"color:#FFD700\"> <B>DBO objects are gold</B></span>";
	public static final String CENTER_MANAGER_HELP_HTML = "<span style=\"line-height:1.4;font-size:15px;margin-top:-10px;\"> The left tree displays all types of objects that reside in AMIDB(Center). Select items in the tree to see a graph displaying the linkage between them. Right click anywhere to view available actions."
			+ "<br>" + TABLE_OBJECTS_HTML + "," + TIMER_OBJECTS_HTML + "," + TRIGGER_OBJECTS_HTML + "," + PROCEDURE_OBJECTS_HTML + "," + METHOD_OBJECTS_HTML + " and"
			+ DBO_OBJECTS_HTML + ".</span>";
	public static final String MINIMAL_HELP_HTML = TABLE_OBJECTS_HTML + "," + TIMER_OBJECTS_HTML + "," + TRIGGER_OBJECTS_HTML + "," + PROCEDURE_OBJECTS_HTML + ","
			+ METHOD_OBJECTS_HTML + " and" + DBO_OBJECTS_HTML + ".</span>";
	private final AmiWebHeaderPortlet header;
	private AmiWebCenterManagerPortlet owner;
	private final boolean allowModification;

	//add buttons to filter only the user defined center objects
	private FormPortletButton showUserDefinedOnlyBtn;

	public AmiWebCenterManagerHeaderPortlet(AmiWebCenterManagerPortlet owner, AmiWebHeaderPortlet header, boolean allowModification) {
		this.owner = owner;
		this.header = header;
		this.allowModification = allowModification;
		StringBuilder legendHtml = new StringBuilder();
		legendHtml.append("<div style=\"height:100%; width:100%; padding:0px 40px; padding-bottom:40px;\">");
		String legendIconPrefix = "<div style=\"display:inline-flex; position:relative; height:100%; width:20%;\"><div style=\"margin:auto; position:relative;\"><div class=\"";
		String legendIconMiddle = "\"></div><div style=\"width:100%; color:white; text-align:center;\">";
		String legendIconSuffix = "</div></div></div>";
		//TODO: These should be replaced with center object icons
		legendHtml.append(legendIconPrefix + "ami_datamodeler_ds" + legendIconMiddle + "Datasource" + legendIconSuffix);
		legendHtml.append(legendIconPrefix + "ami_datamodeler_dm" + legendIconMiddle + "Datamodel" + legendIconSuffix);
		legendHtml.append(legendIconPrefix + "ami_datamodeler_pt" + legendIconMiddle + "Panel" + legendIconSuffix);
		legendHtml.append(legendIconPrefix + "ami_datamodeler_blender" + legendIconMiddle + "Blender" + legendIconSuffix);
		legendHtml.append(legendIconPrefix + "ami_datamodeler_filter" + legendIconMiddle + "Filter" + legendIconSuffix + "</div>");

		RootPortlet root = (RootPortlet) owner.getService().getPortletManager().getRoot();
		int height = root.getHeight();
		this.header.setLegendWidth(300);
		this.header.updateLegendPortletLayout(legendHtml.toString());
		if (height < 888) {
			int scaledHeight = (int) (height * 0.18);
			this.header.setInformationHeaderHeight(scaledHeight);
			this.header.updateBlurbPortletLayout("AMIDB Manager", MINIMAL_HELP_HTML);
		} else {
			this.header.setInformationHeaderHeight(200);
			this.header.updateBlurbPortletLayout("AMIDB Manager", CENTER_MANAGER_HELP_HTML);
		}
		this.header.setSearchHandler(this);
		this.header.getBarFormPortlet().addFormPortletListener(this);
		showUserDefinedOnlyBtn = new FormPortletButton(SHOW_USER_DEFINED_ONLY);
		FormPortlet bar = header.getBarFormPortlet();
		bar.addButton(showUserDefinedOnlyBtn);
		bar.getFormPortletStyle().setButtonPanelStyle("_cna=ami_header_buttons_panel");
		header.updateBarPortletLayout(showUserDefinedOnlyBtn.getHtmlLayoutSignature());
		updateShowUsrDefinedBtn();
	}

	//TODO: for now use the same css class as "ami_datamodeler_show_dividers"
	public void updateShowUsrDefinedBtn() {
		showUserDefinedOnlyBtn.setCssStyle(this.owner.getShowUserDefinedOnlyObjects() ? "_cn=ami_datamodeler_show_dividers" : "_cn=ami_datamodeler_hide_dividers");
	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		if (this.allowModification && button == this.showUserDefinedOnlyBtn) {
			this.owner.setShowUserDefinedOnlyObjects(!owner.getShowUserDefinedOnlyObjects());
		}

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
	public void doSearch() {
		// TODO Auto-generated method stub

	}

	@Override
	public void doSearchNext() {
		// TODO Auto-generated method stub

	}

	@Override
	public void doSearchPrevious() {
		// TODO Auto-generated method stub

	}

}
