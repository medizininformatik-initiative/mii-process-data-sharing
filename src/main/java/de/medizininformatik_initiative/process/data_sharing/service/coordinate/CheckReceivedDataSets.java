package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import org.camunda.bpm.engine.delegate.DelegateExecution;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;

import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class CheckReceivedDataSets extends AbstractServiceDelegate
{
	public CheckReceivedDataSets(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		if (variables.getTargets().isEmpty())
			variables.setBoolean(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_ALL_DATA_SETS_RECEIVED, true);
	}
}
