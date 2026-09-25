package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.variables.Variables;

public class CheckReceivedDataSets implements ServiceTask
{
	public CheckReceivedDataSets()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		if (variables.getTargets().isEmpty())
			variables.setBoolean(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_ALL_DATA_SETS_RECEIVED, true);
	}
}
