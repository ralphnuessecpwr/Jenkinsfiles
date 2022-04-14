import static com.bmc.db2.bmcclient.BMCClientCN.bmcCN;
import static com.bmc.db2.bmcclient.BMCClientSM.bmcSM;
import static com.bmc.db2.bmcclient.BMCClientSA.bmcSA;
import hudson.model.Result;
boolean debug = true
node {
    bmcCN.reset("BMC_SCHEMA_IDENTICAL")
    bmcCN.reset("BMC_GENERATE_JCL_ONLY")
    bmcCN.reset("BMC_SKIP_CDL_GENERATION")
    bmcCN.reset("BMC_RESET_RC")
	
	bmcSM.reset("BMC_SCHEMA_IDENTICAL")
    bmcSM.reset("BMC_GENERATE_JCL_ONLY")
    bmcSM.reset("BMC_SKIP_CDL_GENERATION")
    bmcSM.reset("BMC_RESET_RC")
	
	bmcSA.reset("BMC_SCHEMA_IDENTICAL")
    bmcSA.reset("BMC_GENERATE_JCL_ONLY")
    bmcSA.reset("BMC_SKIP_CDL_GENERATION")
    bmcSA.reset("BMC_RESET_RC")

    // Flag if Compare Step show no changes
    boolean Compare_Equal
    // Flag if Compare Plugin set to Generate JCL Only
    boolean Gen_Only
    // Flag if Job should start with CDL Import
    boolean Skip_Compare
    // Flag to skip Stages if JCL was not submitted or Compare was equal
    boolean Skip_Stage = false

    // Get Build Number as integer so I can build the JOBID dynamically
    if (debug) {
        echo " ${params.Job_ID}"
        echo "${BUILD_NUMBER}"
    }

    stage('Check in code to Source Code Manager') {
		if (!Skip_Stage) {
            build job: 'Check-in Code',
                parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                    string(name: 'Package_Name', value: "${params.Package_Name}"),
                    string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                    password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}")
                ]
        } else {
            echo "Stage Skipped"
        }
    }

    stage('Import DDL to Target System') {
        if (!Skip_Stage) {
            build job: 'Capture DDL',
                parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                    string(name: 'Client_DDL_Directory_Name', value: "${params.Client_DDL_Directory_Name}"),
                    string(name: 'Client_DDL_File_Name', value: "${params.Client_DDL_File_Name}"),
                    string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                    password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}")
                ]
        } else {
            echo "Stage Skipped"
        }
    }
	
	bmcSM.reset("BMC_SCHEMA_STANDARDS_RC")
    stage('Check Schema Standards on DDL') {
        if (!Skip_Stage) {
                build job: 'Check Schema Standards',
                    parameters: [string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                        password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
                        string(name: 'Job_ID', value: "${params.Job_ID}")
                    ]
                    script {
                    JobRC = bmcSM.getRC("BMC_SCHEMA_STANDARDS_RC")
                    echo "JobRC=====start"
                    echo "RC= : " + JobRC
                    echo "JobRC=====end"
                }
                    
                if (JobRC == '0000') {
                    echo "Continuing..."
                } else {
					build job: 'Receive Report',
                    parameters: [string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                        password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
						string(name: 'Output_DIR', value: "${params.Output_DIR}"),
                        string(name: 'Job_ID', value: "${params.Job_ID}"),
						string(name: 'Directory', value: "TDE.${params.TSO_ID}.SCHSTAND.REPORT")
                    ]
					
                    emailext attachmentsPattern: '${Job_ID}_*.txt',
                body: '''Please review the Schema Standards report requested by the developer related to the schema changes of the DDL.
This report is generated due to violations against rules. See attached report.  

You can take a look at the violations on AMI Command Center for Db2 with the following link:
https://bmca.bmc.com:3683/commandcenter/index.html

You can approve or reject implementation of changes on Subsystem DJJ by using this link:
${BUILD_URL}/console''',
                subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - Approve Change Request',
                to: '${Mail_Addr}'

                    echo "Seeking Release Manager approval for Schema changes in process..."
                    stage('Seek Release Manager approval for Schema changes') {
                        if (!Skip_Stage) {
                            input "Submit job to change schema definitions even though violations found?"

                        } else {
                            echo "Stage Get Approval Skipped"
                        }
                    }
                }
        }
		else {
            echo "Stage Skipped"
        }
    }

    stage('Compare Developer Schema definition against Production') {
        build job: 'Compare DDL',
            parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}")
            ]

        Compare_Equal = bmcSM.get("BMC_SCHEMA_IDENTICAL")
        Gen_Only = bmcSM.get("BMC_GENERATE_JCL_ONLY")
        Skip_Compare = bmcSM.get("BMC_SKIP_CDL_GENERATION")

        if (debug) {
            println "Return from Compare " + bmcSM.get("BMC_SCHEMA_IDENTICAL")
            println "Gen JCL Only " + bmcSM.get("BMC_GENERATE_JCL_ONLY")
            println "Skip Compare CDL Gen " + bmcSM.get("BMC_SKIP_CDL_GENERATION")
        }
        if (Compare_Equal) {
            Skip_Stage = true
        } else if (Gen_Only) {
            Skip_Stage = true
        }

        if (!Skip_Stage) {
            build job: 'Download Output',
                parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                    string(name: 'Output_DIR', value: "${params.Output_DIR}"),
                    string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                    password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}")
                ]

        } else {
            if (Compare_Equal) {
                println "Compare showed no differences"
            } else if (Gen_Only) {
                println "Compare JCL Generated Only, not submitted"
            }
        }
    }
	
	stage('Deploy Schema Changes') {
            build job: 'Execute Changes',
                parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                    string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                    password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}")
                ]
	}

    stage('Pre-compile, Compile and Link-Edit Source') {
        if (!Skip_Stage) {
            build job: 'Compile Package',
                parameters: [
                    string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                    password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
                    string(name: 'Package_Name', value: "${params.Package_Name}"),
					string(name: 'Collection_Name', value: "${params.Collection_Name}"),
					string(name: 'Plan_Name', value: "${params.Plan_Name}")
                ]
        } else {
            echo "Stage Skipped"
        }
    }
	
	stage('Bind Package') {
        if (!Skip_Stage) {
            build job: 'Bind Package',
                parameters: [
                    string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                    password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
                    string(name: 'Package_Name', value: "${params.Package_Name}"),
                    string(name: 'Collection_Name', value: "${params.Collection_Name}"),
                    string(name: 'Plan_Name', value: "${params.Plan_Name}")
                ]
        } else {
            echo "Stage Skipped"
        }
    }
	
    bmcSA.reset("BMC_STATIC_RC")
	stage('Analyze Static SQL') {
        if (!Skip_Stage) {
            build job: 'Check Package',
                parameters: [
                    string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                    password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
                    string(name: 'Package_Name', value: "${params.Package_Name}"),
                    string(name: 'Collection_Name', value: "${params.Collection_Name}"),
                    string(name: 'Plan_Name', value: "${params.Plan_Name}")
                ]
                
                script {
                    JobRC = bmcSA.getRC("BMC_STATIC_RC")
                    echo "JobRC=====start"
                    echo "Return Code: " + JobRC
                    echo "JobRC=====end"
                }
                
                if (JobRC == '0000') {
                    echo "Check OK!"
					
					stage('Next Stages') {
						echo "The application was built"
						sleep time: 2138, unit: 'MILLISECONDS'
					}
						
					stage('Cleanup') {
						sleep time: 385, unit: 'MILLISECONDS'
						echo "I deployed"
					}
                    
                } else {
                build job: 'Receive Report',
                    parameters: [
                        string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                        password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
                        string(name: 'Output_DIR', value: "${params.Output_DIR}"),
                        string(name: 'Job_ID', value: "${Job_ID}"),
						string(name: 'Directory', value: "TDE.${params.TSO_ID}.SQL.REPORT")
                            ]
                        
                    emailext attachmentsPattern: '${Job_ID}_*.txt',
                body: '''Please review the explain report. 
''',
                subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - Package Report',
                to: '${Mail_Addr}'
				
				script {
                    env.RELEASE_SCOPE = input message: 'Exceptions found. Which procedure do you want to continue with?',
                      parameters: [choice(name: 'Procedures:', choices: 'Rollback the Changes\nContinue with the Deployment', description: 'What is the procedure?')]
                }
                echo "${env.RELEASE_SCOPE}"
				
				if (env.RELEASE_SCOPE == 'Rollback the Changes') {
					
					stage('Rollback Initiated - Rollback Schema Changes') {
                    build job: 'Rollback',
                        parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                            string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                            password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
							string(name: 'Package_Name', value: "${params.Package_Name}"),
							string(name: 'Collection_Name', value: "${params.Collection_Name}"),
							string(name: 'Plan_Name', value: "${params.Plan_Name}")
                        ]
					}
					
					stage('Rollback Initiated - Rollback Package') {
					build job: 'Compile Package',
						parameters: [
							string(name: 'TSO_ID', value: "${params.TSO_ID}"),
							password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
							string(name: 'Package_Name', value: "${params.Package_Name}"),
							string(name: 'Collection_Name', value: "${params.Collection_Name}"),
							string(name: 'Plan_Name', value: "${params.Plan_Name}")
						]
					
					build job: 'Bind Package',
						parameters: [
							string(name: 'TSO_ID', value: "${params.TSO_ID}"),
							password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
							string(name: 'Package_Name', value: "${params.Package_Name}"),
							string(name: 'Collection_Name', value: "${params.Collection_Name}"),
							string(name: 'Plan_Name', value: "${params.Plan_Name}")
						]
					}
					
					stage('Rollback Initiated - Cleanup') {
					build job: 'Cleanup',
						parameters: [
							string(name: 'TSO_ID', value: "${params.TSO_ID}"),
							password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
							string(name: 'Job_ID', value: "${Job_ID}")
						]
					}
				}
				
			else {
				stage('Next Stages') {
					echo "The application was built"
					sleep time: 2138, unit: 'MILLISECONDS'
				}
						
				stage('Cleanup') {
					sleep time: 385, unit: 'MILLISECONDS'
					echo "I deployed"
				}
			}
			}
		}
    }
}