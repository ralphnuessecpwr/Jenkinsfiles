package com.compuware.devops.util

class PipelineConfig
{

/* 
Class to hold Jenkins server specific setup information
*/

public String Git_Target_Branch    = "CONS"
public String Git_Branch           = "master"
public String SQ_Scanner_Name      = "scanner" //"Scanner" //"scanner" 
public String SQ_Server_Name       = "localhost"  //"CWCC" //"localhost"  
public String SQ_Server_URL        = 'http://sonarqube.nasa.cpwr.corp:9000'
public String MF_Source            = "MF_Source"
public String XLR_Template         = "A Release from Jenkins" //"A Release from Jenkins - RNU" //"A Release from Jenkins"
public String XLR_User             = "admin"    //"xebialabs" //"admin"                           
public String TTT_Folder           = "tests"	
public String ISPW_URL             = "http://cwcc.compuware.com:2020"
public String ISPW_Runtime         = "ispw"		 

}