package com.f1.ami.web.centermanager.autocomplete;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.f1.ami.amicommon.AmiUtils;
import com.f1.ami.amicommon.functions.AmiWebFunctionEval;
import com.f1.ami.amicommon.functions.AmiWebFunctionFactory;
import com.f1.ami.amicommon.functions.AmiWebFunctionIsInstanceOf;
import com.f1.ami.amicommon.functions.AmiWebFunctionStrClassName;
import com.f1.ami.amiscript.AmiScriptMemberMethods;
import com.f1.ami.web.AmiWebDebugManagerImpl;
import com.f1.ami.web.AmiWebService;
import com.f1.utils.CH;
import com.f1.utils.LH;
import com.f1.utils.sql.SqlProcessor;
import com.f1.utils.structs.table.derived.BasicMethodFactory;
import com.f1.utils.structs.table.derived.DeclaredMethodFactory;
import com.f1.utils.structs.table.derived.MethodFactory;
import com.f1.utils.structs.table.derived.ThreadSafeMethodFactoryManager;

//keep, add to AmiWebFormPortletAmiScriptField
public class AmiCenterManagerImdbScriptManager {
	private static final Logger log = LH.get();
	final private AmiWebService service;
	private BasicMethodFactory methodFactory;
	final private BasicMethodFactory predefinedMethodsFactory;//SYSTEM
	final private BasicMethodFactory declaredMethodsFactory;//CONFIG (from others.amisql)
	final private BasicMethodFactory managedMethodsFactory;//USER (from managed_schema.amksql)

	public AmiCenterManagerImdbScriptManager(AmiWebService service) {
		this.service = service;
		this.methodFactory = new ThreadSafeMethodFactoryManager();
		this.declaredMethodsFactory = new BasicMethodFactory();
		this.managedMethodsFactory = new BasicMethodFactory();
		this.predefinedMethodsFactory = new BasicMethodFactory();
		AmiUtils.addTypes(predefinedMethodsFactory);
		//this.service.addTimedEvent(time, layoutAlias, script, calc);
		SqlProcessor sp = new SqlProcessor();
		List<AmiWebFunctionFactory> funcs = CH.l(AmiUtils.getFunctions());
		funcs.add(new AmiWebFunctionIsInstanceOf.Factory(predefinedMethodsFactory));
		funcs.add(new AmiWebFunctionStrClassName.Factory(predefinedMethodsFactory));
		funcs.add(new AmiWebFunctionEval.Factory(sp.getParser(), predefinedMethodsFactory));

		AmiScriptMemberMethods.registerMethods(new AmiWebDebugManagerImpl(this.service), predefinedMethodsFactory);

		this.methodFactory.clearFactoryManagers();
		this.methodFactory.addFactoryManager(predefinedMethodsFactory);
		this.methodFactory.addFactoryManager(managedMethodsFactory);
		this.methodFactory.addFactoryManager(declaredMethodsFactory);
		for (AmiWebFunctionFactory f : funcs)
			predefinedMethodsFactory.addFactory(f);
	}

	public BasicMethodFactory getMethodFactory() {
		return this.methodFactory;
	}

	public void setMethodFactory(BasicMethodFactory methodFactory) {
		this.methodFactory = methodFactory;
	}

	public BasicMethodFactory getDeclaredMethodFactories() {
		return this.declaredMethodsFactory;
	}

	public void addDeclaredMethodFactory(MethodFactory toAdd) {
		this.declaredMethodsFactory.addFactory(toAdd);
	}

	public void addManagedMethodFactory(MethodFactory toAdd) {
		this.managedMethodsFactory.addFactory(toAdd);
	}

	//this is to mimic AmiSchema::writeManagedSchemaFile()->MethodFactoryManager mf = this.imdb.getScriptManager().getManagedMethodFactory();
	public BasicMethodFactory getManagedMethodFactory() {
		return this.managedMethodsFactory;
	}

	public List<DeclaredMethodFactory> getManagedMethodFactories() {
		BasicMethodFactory mf = getManagedMethodFactory();
		List<DeclaredMethodFactory> sink2 = new ArrayList<DeclaredMethodFactory>();
		if (mf != null) {
			List<MethodFactory> sink = new ArrayList<MethodFactory>();
			mf.getAllMethodFactories(sink);
			for (MethodFactory i : sink)
				if (i instanceof DeclaredMethodFactory)
					sink2.add((DeclaredMethodFactory) i);
		}
		return sink2;
	}
}
