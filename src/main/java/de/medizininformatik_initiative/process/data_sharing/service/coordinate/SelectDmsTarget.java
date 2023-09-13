package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;

public class SelectDmsTarget extends AbstractServiceDelegate
{

	public SelectDmsTarget(ProcessPluginApi api)
	{
		super(api);
	}


	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		String dms = getDmsIdentifier(variables);
		Target target = getDmsTarget(dms, variables);
		variables.setTarget(target);
	}

	private String getDmsIdentifier(Variables variables)
	{
		return variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

	}

	private Target getDmsTarget(String identifier, Variables variables)
	{
		return api.getEndpointProvider().getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(identifier),
				new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
						.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_DMS))
				.map(e -> variables.createTarget(identifier, e.getIdentifierFirstRep().getValue(), e.getAddress()))
				.orElseThrow(() -> new RuntimeException(
						"No Endpoint of DMS organization with identifier '" + identifier + "' found"));
	}
}
