/**
 *  ecobeeRemoteSensorsInit
 *
 *  Copyright 2015-2020 Yves Racine
 *  LinkedIn profile: ca.linkedin.com/pub/yves-racine-m-sc-a/0/406/4b/
 *
 *  Developer retains all right, title, copyright, and interest, including all copyright, patent rights, trade secret 
 *  in the Background technology. May be subject to consulting fees under the Agreement between the Developer and the Customer. 
 *  Developer grants a non exclusive perpetual license to use the Background technology in the Software developed for and delivered 
 *  to Customer under this Agreement. However, the Customer shall make no commercial use of the Background technology without
 *  Developer's written consent.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *  Software Distribution is restricted and shall be done only with Developer's written approval.
 *
 *  For installation, please refer to readme file under
 *     https://github.com/yracine/device-type.myecobee/blob/master/smartapps/ecobeeRemoteSensor.md
 *
 *
 */
definition(
	name: "${get_APP_NAME()}",
	namespace: "yracine",
	author: "Yves Racine",
	description: "Create individual ST sensors for all selected ecobee3's remote sensors and update them on a regular basis (interval chosen by the user).",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee@2x.png"
)


def get_APP_VERSION() {return "2.9.2"}

preferences {

	page(name: "selectThermostat", title: "Ecobee Thermostat", install: false, uninstall: true, nextPage: "selectEcobeeSensors") {
		section("About") {
			paragraph  image:"${getCustomImagePath()}ecohouse.jpg", "${get_APP_NAME()}, the smartapp that creates individual ST sensors for your ecobee's remote Sensors and polls them on a regular basis"
			paragraph "Version ${get_APP_VERSION()}"
			paragraph "If you like this smartapp, please support the developer via PayPal and click on the Paypal link below " 
				href url: "https://www.paypal.me/ecomatiqhomes",
					title:"Paypal donation..."
			paragraph "Copyright©2014 Yves Racine"
				href url:"http://github.com/yracine/device-type.myecobee", style:"embedded", required:false, title:"More information..." 
		}
		section("Select the ecobee thermostat") {
			input "ecobee", "capability.thermostat", title: "Which ecobee thermostat?"

		}
		section("Polling ecobee's remote sensor(s) at which interval in minutes (range=[5,10,15,30],default =5 min.)?") {
			input "givenInterval", "number", title: "Interval", required: false
		}

	}
	page(name: "selectEcobeeSensors", title: "Ecobee Remote Sensors", content: "selectEcobeeSensors", nextPage: "Notifications")
	page(name: "watchdogSettingsSetup")   
	page(name: "Notifications", title: "Other Options", install: true, uninstall: true) {
		section("Handle/Notify any exception proactively") {
			input "handleExceptionFlag", "bool", title: "Handle exceptions proactively?", required: false
		}
		section("Notifications & Logging") {
			input "sendPushMessage", "enum", title: "Send a push notification?", metadata: [values: ["Yes", "No"]], required:
				false
			input "phoneNumber", "phone", title: "Send a text message?", required: false
			input "detailedNotif", "bool", title: "Detailed Logging & Notifications?", required:false
			input "logFilter", "enum", title: "log filtering [Level 1=ERROR only,2=<Level 1+WARNING>,3=<2+INFO>,4=<3+DEBUG>,5=<4+TRACE>]?", required:false, metadata: [values: [1,2,3,4,5]]
		}
		section("Scheduler's watchdog Settings (needed if any ST scheduling issues)") {
			href(name: "toWatchdogSettingsSetup", page: "watchdogSettingsSetup",required:false,  description: "Optional",
				title: "Scheduler's watchdog Settings", image: "${getCustomImagePath()}safeguards.jpg" ) 
		}
		section("Enable Amazon Echo/Ask Alexa Notifications [optional, default=false]") {
			input (name:"askAlexaFlag", title: "Ask Alexa verbal Notifications?", type:"bool",
				description:"optional",required:false)
			input (name:"listOfMQs",  type:"enum", title: "List of the Ask Alexa Message Queues (default=Primary)", options: state?.askAlexaMQ, multiple: true, required: false,
				description:"optional")            
			input "AskAlexaExpiresInDays", "number", title: "Ask Alexa's messages expiration in days (default=2 days)?", required: false
		}
		section([mobileOnly: true]) {
			label title: "Assign a name for this SmartApp", required: false
			mode title: "Set for specific mode(s)", required: false
		}
	}
}


def watchdogSettingsSetup() {
	dynamicPage(name: "watchdogSettingsSetup", title: "Scheduler's Watchdog Settings ", uninstall: false) {
		section("Watchdog options: the watchdog should be a single polling device amongst the choice of sensors below. The watchdog needs to be regularly polled every 5-10 minutes and will be used as a 'heartbeat' to reschedule if needed.") {
			input (name:"tempSensor", type:"capability.temperatureMeasurement", title: "What do I use as temperature sensor to restart smartapp processing?",
				required: false, description: "Optional Watchdog- just use a single polling device")
			input (name:"motionSensor", type:"capability.motionSensor", title: "What do I use as a motion sensor to restart smartapp processing?",
				required: false, description: "Optional Watchdog -just use a single polling device")
			input (name:"energyMeter", type:"capability.powerMeter", title: "What do I use as energy sensor to restart smartapp processing?",
				required: false, description: "Optional Watchdog-  just use a single polling device")
			input (name:"powerSwitch", type:"capability.switch", title: "What do I use as Master on/off switch to restart smartapp processing?",
				required: false, description: "Optional Watchdog - just use a single  polling device")
		}
		section {
			href(name: "toOtherSettingsPage", title: "Back to Other Settings Page", page: "Notifications")
		}
        
	}
}  

def selectEcobeeSensors() {

	def sensors = [: ]
	/* Generate the list of all remote sensors available 
	*/
	try {
		ecobee.generateRemoteSensorEvents("", false, true)
	} catch (e) {
		log.debug "selectEcobeeSensors>exception $e when getting list of Remote Sensors, exiting..."
		return sensors
	}    

	/* Get only the list of all occupancy remote sensors available 
	*/

	def ecobeeSensors = ecobee.currentRemoteSensorOccData.toString().minus('[').minus(']').split(",,")

	traceEvent(settings.logFilter,"selectEcobeeSensors>ecobeeSensors= $ecobeeSensors", detailedNotif)

	if (!ecobeeSensors) {

		traceEvent(settings.logFilter,"selectEcobeeSensors>no values found", detailedNotif)
		return sensors

	}

	for (i in 0..ecobeeSensors.size() - 1) {

		def ecobeeSensorDetails = ecobeeSensors[i].split(',')
		def ecobeeSensorId = ecobeeSensorDetails[0]
		def ecobeeSensorName = ecobeeSensorDetails[1]

		def dni = [app.id, ecobeeSensorName, getRemoteSensorChildName(), ecobeeSensorId].join('.')

		sensors[dni] = ecobeeSensorName

	}


	traceEvent(settings.logFilter,"selectEcobeeSensors> sensors= $sensors", detailedNotif)

	int sensorsCount=sensors.size()
	def chosenSensors = dynamicPage(name: "selectEcobeeSensors", title: "Select Ecobee Sensors (${sensorsCount} found)", install: false, uninstall: true) {
		section("Select Remote Sensors") {
			paragraph image: "${getCustomImagePath()}ecobeeSensor.jpg", "select your ecobee sensors to be exposed to SmartThings."
			input(name: "remoteSensors", title: "", type: "enum", required: false, multiple: true, description: "Tap to choose", metadata: [values: sensors])
		}
	}
	return chosenSensors
}



def installed() {
	settings.detailedNotif=true 		// initial value
	settings.logFilter=get_LOG_DEBUG()	// initial value
	traceEvent(settings.logFilter,"Installed with settings: ${settings}",detailedNotif)
	initialize()
}

def updated() {
	traceEvent(settings.logFilter,"Updated with settings: ${settings}", detailedNotif)
	unsubscribe()
	unschedule()
	initialize()
}


def terminateMe() {
	try {
		app.delete()
	} catch (Exception e) {
		traceEvent(settings.logFilter, "terminateMe>failure, exception $e", get_LOG_ERROR(), true)
	}
}


def purgeChildDevice(childDevice) {
	def dni = childDevice.device.deviceNetworkId
	def foundRemote=remoteSensors.find {dni}    
	if (foundRemote) {
		remoteSensors.remove(dni)
		app.updateSetting("remoteSensors", remoteSensors ? remoteSensors : [])
	}        
	if (getChildDevices().size <= 1) {
		traceEvent(settings.logFilter,"purgeChildDevice>no more devices to poll, unscheduling and terminating the app", get_LOG_ERROR())
		unschedule()
		atomicState.authToken=null
		runIn(1, "terminateMe")
	}
}


def rescheduleHandler(evt) {
	traceEvent(settings.logFilter,"$evt.name: $evt.value", detailedNotif)
	rescheduleIfNeeded()		
}



private def createRemoteSensors() {

	def devices = remoteSensors.collect {dni->
		def d = getChildDevice(dni)
		traceEvent(settings.logFilter,"initialize>Looping thru motion Sensors, found id $dni", detailedNotif)

		if (!d) {
			def sensor_info = dni.tokenize('.')
			def sensorId = sensor_info.last()
			def sensorName = sensor_info[1]
			def tstatName = ecobee.currentThermostatName            
 
			def labelName = "${tstatName}:${sensorName}"
			traceEvent(settings.logFilter,"About to create child device with id $dni, sensorId = $sensorId, labelName=  ${labelName}", detailedNotif)
			d = addChildDevice(getChildNamespace(), getRemoteSensorChildName(), dni, null, [label: "${labelName}"])
			traceEvent(settings.logFilter,"created ${d.displayName} with id $dni", detailedNotif)
		} else {
			traceEvent(settings.logFilter,"initialize>found ${d.displayName} with id $dni already exists", detailedNotif)
		}

	}
	traceEvent(settings.logFilter,"ecobeeRemoteSensorsInit>created ${devices.size()} MyEcobee's Remote Sensors" , detailedNotif)

}


private def deleteRemoteSensors() {

	def delete
	// Delete any that are no longer in settings
	if (!remoteSensors) {
		traceEvent(settings.logFilter,"delete all Remote Sensors", detailedNotif)
		delete = getChildDevices().findAll {
			it.device.deviceNetworkId.contains(getRemoteSensorChildName())
		}
	} else {
		delete = getChildDevices().findAll {
			((it.device.deviceNetworkId.contains(getRemoteSensorChildName())) && (!remoteSensors.contains(it.device.deviceNetworkId)))
		}
	}		
	traceEvent(settings.logFilter,"ecobeeRemoteSensorsInit>deleting ${delete.size()} MyEcobee's Remote Sensors", detailedNotif)
	delete.each {
		try {    
			deleteChildDevice(it.deviceNetworkId)
		} catch(e) {
			traceEvent(settings.logFilter,"ecobeeRemoteSensorsInit>exception $e while trying to delete Remote Sensor ${it.deviceNetworkId}", detailedNotif, get_LOG_ERROR())
		}        
	}


}


def initialize() {
	traceEvent(settings.logFilter,"initialize>begin",detailedNotif, get_LOG_TRACE())

    
	state?.exceptionCount=0       
	state?.runtimeRevision=null
/*    
	subscribe(ecobee, "remoteSensorOccData", updateRemoteSensors)
	subscribe(ecobee, "remoteSensorTmpData", updateRemoteSensors)
	subscribe(ecobee, "remoteSensorHumData", updateRemoteSensors)
*/


	Integer delay = givenInterval ?: 5 // By default, do it every 5 min.
    
	traceEvent(settings.logFilter,"ecobeeRemoteSensorsInit>scheduling takeAction every ${delay} minutes", detailedNotif)
	state?.poll = [ last: 0, rescheduled: now() ]

	//Subscribe to different events (ex. sunrise and sunset events) to trigger rescheduling if needed
	subscribe(location, "sunrise", rescheduleIfNeeded)
	subscribe(location, "sunset", rescheduleIfNeeded)
	subscribe(location, "mode", rescheduleIfNeeded)
	subscribe(location, "sunriseTime", rescheduleIfNeeded)
	subscribe(location, "sunsetTime", rescheduleIfNeeded)
	if (powerSwitch) {
		subscribe(powerSwitch, "switch.off", rescheduleHandler, [filterEvents: false])
		subscribe(powerSwitch, "switch.on", rescheduleHandler, [filterEvents: false])
	}
	if (tempSensor)	{
		subscribe(tempSensor,"temperature", rescheduleHandler,[filterEvents: false])
	}
	if (motionSensor)	{
		subscribe(motionSensor,"motion", rescheduleHandler,[filterEvents: false])
	}

	if (energyMeter)	{
		subscribe(energyMeter,"energy", rescheduleHandler,[filterEvents: false])
	}

	subscribe(app, appTouch)

	traceEvent(settings.logFilter,"initialize>polling delay= ${delay}...", detailedNotif, get_LOG_TRACE())
	subscribe(location, "askAlexaMQ", askAlexaMQHandler)
	deleteRemoteSensors()
	createRemoteSensors()

	rescheduleIfNeeded()   
}

def askAlexaMQHandler(evt) {
	if (!evt) return
	switch (evt.value) {
		case "refresh":
		state?.askAlexaMQ = evt.jsonData && evt.jsonData?.queues ? evt.jsonData.queues : []
		break
	}
}

def appTouch(evt) {
	rescheduleIfNeeded()
}


def rescheduleIfNeeded(evt) {
	if (evt) traceEvent(settings.logFilter,"rescheduleIfNeeded>$evt.name=$evt.value", detailedNotif)
	Integer delay = givenInterval ?: 5 // By default, do it every 5 min.
	BigDecimal currentTime = now()    
	BigDecimal lastPollTime = (currentTime - (state?.poll["last"]?:0))  
	if (lastPollTime != currentTime) {    
		Double lastPollTimeInMinutes = (lastPollTime/60000).toDouble().round(1)      
		traceEvent(settings.logFilter,"rescheduleIfNeeded>last poll was  ${lastPollTimeInMinutes.toString()} minutes ago",detailedNotif, get_LOG_INFO())
	}
	if (((state?.poll["last"]?:0) + (delay * 60000) < currentTime) && canSchedule()) {
		traceEvent(settings.logFilter,"rescheduleIfNeeded>scheduling takeAction in ${delay} minutes..", detailedNotif, get_LOG_INFO())
		if ((delay >=0) && (delay <10)) {      
			runEvery5Minutes(takeAction)
		} else if ((delay >=10) && (delay <15)) {  
			runEvery10Minutes(takeAction)
		} else if ((delay >=15) && (delay <30)) {  
			runEvery15Minutes(takeAction)
		} else {  
			runEvery30Minutes(takeAction)
		}
		takeAction()
	}
    
	// Update rescheduled state
    
	if (!evt) state.poll["rescheduled"] = now()
}

def takeAction() {

	def todayDay
    
	if (!location.timeZone) {    	
		traceEvent(settings.logFilter,"takeAction>Your location is not set in your ST account, you'd need to set it as indicated in the prerequisites for better exception handling..",
			true,get_LOG_ERROR(),true)
	} else {
		todayDay = new Date().format("dd",location.timeZone)
	}        

	if ((!state?.today) || (todayDay != state?.today)) {
		state?.exceptionCount=0   
		state?.sendExceptionCount=0        
		state?.today=todayDay        
	}   
	boolean handleException = (handleExceptionFlag)?: false
	traceEvent (settings.logFilter,"takeAction>begin", detailedNotif, get_LOG_TRACE())
	Integer delay = givenInterval ?: 5 // By default, do it every 5 min.
	state?.poll["last"] = now()
		
	//schedule the scheduleIfNeeded() function
    
	if (((state?.poll["rescheduled"]?:0) + (delay * 60000)) < now()) {
		traceEvent(settings.logFilter,"takeAction>scheduling rescheduleIfNeeded() in ${delay} minutes..", detailedNotif, get_LOG_INFO())
		schedule("0 0/${delay} * * * ?", rescheduleIfNeeded)
		// Update rescheduled state
		state?.poll["rescheduled"] = now()
	}

    
	def MAX_EXCEPTION_COUNT=10
	String exceptionCheck
	traceEvent(settings.logFilter,"takeAction>about to call generateRemoteSensorEvents()", detailedNotif)
	ecobee.refresh()
	ecobee.generateRemoteSensorEvents("", false)	
	exceptionCheck= ecobee.currentVerboseTrace.toString()
	if (handleException) {            
		if ((exceptionCheck) && ((exceptionCheck.contains("exception") || (exceptionCheck.contains("error")) && 
			(!exceptionCheck.contains("Java.util.concurrent.TimeoutException"))))) {  
			// check if there is any exception or an error reported in the verboseTrace associated to the device (except the ones linked to rate limiting).
			state?.exceptionCount=state.exceptionCount+1    
			traceEvent(settings.logFilter,"takeAction>found exception/error after calling generateRemoteSensorEvents, exceptionCount= ${state?.exceptionCount}: $exceptionCheck",
				true, get_LOG_ERROR())            
		} else {             
			// reset exception counter            
			state?.exceptionCount=0       
		}
	} /* end if handleException */            
	if (handleException) {            
		if (state?.exceptionCount>=MAX_EXCEPTION_COUNT) {
			// may need to authenticate again    
			traceEvent(settings.logFilter,"too many exceptions/errors, $exceptionCheck (${state?.exceptionCount} errors), you may need to re-authenticate at ecobee...", detailedNotif, 
				get_LOG_ERROR(),true)
		}            
	}    
    
	updateRemoteSensors()

	traceEvent(settings.logFilter,"takeAction>end", detailedNotif)
}



private updateRemoteSensors(evt) {
	traceEvent(settings.logFilter,"updateRemoteSensors>evt name=$evt.name, evt.value= $evt.value", detailedNotif)

	updateRemoteSensors()
}

private updateRemoteSensors() {
	updateMotionValues()	
	updateTempValues()
//	updateHumidityValues()
}


private updateMotionValues() {

	def ecobeeSensors = ecobee.currentRemoteSensorOccData.toString().minus('[').minus(']').split(",,")
	traceEvent(settings.logFilter,"updateMotionValues>ecobeeRemoteSensorOccData= $ecobeeSensors", detailedNotif)

	if ((!ecobeeSensors) || (ecobeeSensors==[null])) {

		traceEvent(settings.logFilter,"updateMotionValues>no values found", detailedNotif, get_LOG_WARN())
		return
	}
	for (i in 0..ecobeeSensors.size() - 1) {
		def ecobeeSensorDetails = ecobeeSensors[i].split(',')
		def ecobeeSensorId = ecobeeSensorDetails[0]
		def ecobeeSensorName = ecobeeSensorDetails[1]
		def ecobeeSensorType = ecobeeSensorDetails[2]
		String ecobeeSensorValue = ecobeeSensorDetails[3].toString()

		def remoteSensorFound= getChildDevices().findAll {
			it.device.deviceNetworkId.contains(ecobeeSensorId)
		}

		if (remoteSensorFound) {
			traceEvent(settings.logFilter,"updateMotionValues>ecobeeSensorId=$ecobeeSensorId",detailedNotif)
			traceEvent(settings.logFilter,"updateMotionValues>ecobeeSensorName=$ecobeeSensorName",detailedNotif)
			traceEvent(settings.logFilter,"updateMotionValues>ecobeeSensorType=$ecobeeSensorType",detailedNotif)
			traceEvent(settings.logFilter,"updateMotionValues>ecobeeSensorValue=$ecobeeSensorValue",detailedNotif)

			String status = (ecobeeSensorValue.contains('false')) ? "inactive" : "active"
/*            
			boolean isChange = device.isStateChange(device, "motion", status)
			boolean isDisplayed = isChange"device $device, found $dni,statusChanged=${isChange},  value= ${status}",detailedNotif)
*/            
			traceEvent(settings.logFilter,"found device $remoteSensorFound, latest motion value from ecobee API= ${status}", detailedNotif, 
				get_LOG_INFO(),  detailedNotif)

			remoteSensorFound.each {
				it.sendEvent(name: "motion", value: status)
				it.sendEvent(name: "name", value: ecobeeSensorName)
			}                
		} else {

			traceEvent(settings.logFilter,"updateMotionValues>couldn't find device $ecobeeSensorName with id $ecobeeSensorId, probably not selected originally",detailedNotif)
		}

	}

}

private updateTempValues() {

	String tempValueString='',tempValueWithDecimalString=''   
	Double tempValue    
	def scale = getTemperatureScale()
	def ecobeeSensors = ecobee.currentRemoteSensorTmpData.toString().minus('[').minus(']').split(",,")

	traceEvent(settings.logFilter,"updateTempValues>ecobeeRemoteSensorTmpData= $ecobeeSensors",detailedNotif)


	if ((!ecobeeSensors) || (ecobeeSensors==[null])) {

		traceEvent(settings.logFilter,"updateTempSensors>no values found",detailedNotif)
		return
	}

	for (i in 0..ecobeeSensors.size() - 1) {

		def ecobeeSensorDetails = ecobeeSensors[i].split(',')
		def ecobeeSensorId = ecobeeSensorDetails[0]
		def ecobeeSensorName = ecobeeSensorDetails[1]
		def ecobeeSensorType = ecobeeSensorDetails[2]
		def ecobeeSensorValue = ecobeeSensorDetails[3]


		def remoteSensorFound= getChildDevices().findAll {
			it.device.deviceNetworkId.contains(ecobeeSensorId)
		}
		if (remoteSensorFound) {

			traceEvent(settings.logFilter,"updateTempValues>ecobeeSensorId= $ecobeeSensorId",detailedNotif)
			traceEvent(settings.logFilter,"updateTempValues>ecobeeSensorName= $ecobeeSensorName",detailedNotif)
			traceEvent(settings.logFilter,"updateTempValues>ecobeeSensorType= $ecobeeSensorType",detailedNotif)
			traceEvent(settings.logFilter,"updateTempValues>ecobeeSensorValue= $ecobeeSensorValue",detailedNotif)
            
			if (ecobeeSensorValue) {
				if (scale == "F") {
					tempValue = getTemperature(ecobeeSensorValue).round()
					tempValueString = String.format('%2d', tempValue.intValue())            
					tempValueWithDecimalString = String.format('%2.1f', getTemperature(ecobeeSensorValue).round(1))           
				} else {
					tempValue = getTemperature(ecobeeSensorValue).round(1)
					tempValueString = String.format('%2.1f', tempValue)
					tempValueWithDecimalString = String.format('%2.1f', getTemperature(ecobeeSensorValue).round(1))           
				}

				traceEvent(settings.logFilter,"found device $remoteSensorFound, latest temp value from ecobee API= ${tempValueString}", detailedNotif, 
					get_LOG_INFO(), detailedNotif)
				remoteSensorFound.each {
					def isTempDecimalChange = it.isStateChange(it, "temperature", tempValueWithDecimalString)	//check of state change
					it.sendEvent(name: "temperatureDisplay", value: tempValueString, unit: scale, isStateChange:true)
					it.sendEvent(name: "temperature", value: tempValueWithDecimalString, unit: scale, isDisplayed:true, isStateChange: isTempDecimalChange)
					it.sendEvent(name: "name", value: ecobeeSensorName)
				}                    
			}
		} else {
			traceEvent(settings.logFilter,"updateTempValues>couldn't find device $ecobeeSensorName with id $ecobeeSensorId, probably not selected originally",detailedNotif)
		}

	}

}



private updateHumidityValues() {


	def ecobeeSensors = ecobee.currentRemoteSensorHumData.toString().minus('[').minus(']').split(",,")

	traceEvent(settings.logFilter,"updateHumidityValues>ecobeeRemoteSensorHumData= $ecobeeSensors",detailedNotif)

	if (ecobeeSensors.size() < 1) {

		traceEvent(settings.logFilter,"updateHumidityValues>no values found",detailedNotif, get_LOG_WARN())
		return
	}

	for (i in 0..ecobeeSensors.size() - 1) {

		def ecobeeSensorDetails = ecobeeSensors[i].split(',')
		def ecobeeSensorId = ecobeeSensorDetails[0]
		def ecobeeSensorName = ecobeeSensorDetails[1]
		def ecobeeSensorType = ecobeeSensorDetails[2]
		def ecobeeSensorValue = ecobeeSensorDetails[3]


		def remoteSensorFound= getChildDevices().findAll {
			it.device.deviceNetworkId.contains(ecobeeSensorId)
		}

		if (remoteSensorFound) {

			traceEvent(settings.logFilter,"updateHumidityValues>ecobeeSensorId= $ecobeeSensorId",detailedNotif)
			traceEvent(settings.logFilter,"updateHumidityValues>ecobeeSensorName= $ecobeeSensorName",detailedNotif)
			traceEvent(settings.logFilter,"updateHumidityValues>ecobeeSensorType= $ecobeeSensorType",detailedNotif)
			traceEvent(settings.logFilter,"updateHumidityValues>ecobeeSensorValue= $ecobeeSensorValue",detailedNotif)
            
			if (ecobeeSensorValue) {
				Double humValue = ecobeeSensorValue.toDouble().round()
				String humValueString = String.format('%2d', humValue.intValue())
/*
				boolean isChange = device.isStateChange(device, "humidity", humValueString)
				boolean isDisplayed = isChange
*/
				traceEvent(settings.logFilter,"found device $remoteSensorFound, latest hum value from ecobee API= ${humValueString}", detailedNotif, 
					get_LOG_INFO(),  detailedNotif)
				remoteSensorFound.each {
					it.sendEvent(name: "humidity", value: humValueString, unit: '%')
					it.sendEvent(name: "name", value: ecobeeSensorName)
				}                    
			}	
		} else {
				traceEvent(settings.logFilter,"updateHumidityValues>couldn't find device $ecobeeSensorName with id $ecobeeSensorId, no child device found", detailedNotif)
		}	
	}
}



private def getTemperature(value) {
	Double farenheits = value.toDouble()
	if (getTemperatureScale() == "F") {
		return farenheits
	} else {
		return fToC(farenheits)
	}
}


// catchall
def event(evt) {
	traceEvent(settings.logFilter,"value: $evt.value, event: $evt, settings: $settings, handlerName: ${evt.handlerName}", detailedNotif)
}

def cToF(temp) {
	return (temp * 1.8 + 32)
}

def fToC(temp) {
	return (temp - 32) / 1.8
}

def getChildNamespace() {
	"yracine"
}
def getRemoteSensorChildName() {
	"My Remote Sensor"
}



private int get_LOG_ERROR()	{return 1}
private int get_LOG_WARN()	{return 2}
private int get_LOG_INFO()	{return 3}
private int get_LOG_DEBUG()	{return 4}
private int get_LOG_TRACE()	{return 5}

def traceEvent(filterLog, message, displayEvent=false, traceLevel=4, sendMessage=false) {
	int LOG_ERROR= get_LOG_ERROR()
	int LOG_WARN=  get_LOG_WARN()
	int LOG_INFO=  get_LOG_INFO()
	int LOG_DEBUG= get_LOG_DEBUG()
	int LOG_TRACE= get_LOG_TRACE()
	int filterLevel=(filterLog)?filterLog.toInteger():get_LOG_WARN()

	if (filterLevel >= traceLevel) {
		if (displayEvent) {    
			switch (traceLevel) {
				case LOG_ERROR:
					log.error "${message}"
				break
				case LOG_WARN:
					log.warn "${message}"
				break
				case LOG_INFO:
					log.info "${message}"
				break
				case LOG_TRACE:
					log.trace "${message}"
				break
				case LOG_DEBUG:
				default:            
					log.debug "${message}"
				break
			}                
		}			                
		if (sendMessage) send (message,settings.askAlexaFlag) //send message only when true
	}        
}


private send(msg, askAlexa=false) {
	int MAX_EXCEPTION_MSG_SEND=5

	// will not send exception msg when the maximum number of send notifications has been reached
	if (msg.contains("exception")) {
		state?.sendExceptionCount=(state?.sendExceptionCount?:0)+1         
		traceEvent(settings.logFilter,"checking sendExceptionCount=${state?.sendExceptionCount} vs. max=${MAX_EXCEPTION_MSG_SEND}", detailedNotif)
		if (state?.sendExceptionCount >= MAX_EXCEPTION_MSG_SEND) {
			traceEvent(settings.logFilter,"send>reached $MAX_EXCEPTION_MSG_SEND exceptions, exiting", detailedNotif)
			return        
		}        
	}    
	def message = "${get_APP_NAME()}>${msg}"

	if (sendPushMessage == "Yes") {
		traceEvent(settings.logFilter,"contact book not enabled", false, get_LOG_INFO())
		sendPush(message)
	}
	if (askAlexa) {
		def expiresInDays=(AskAlexaExpiresInDays)?:2    
		sendLocationEvent(
			name: "AskAlexaMsgQueue", 
			value: "${get_APP_NAME()}", 
			isStateChange: true, 
			descriptionText: msg, 
			data:[
				queues: listOfMQs,
				expires: (expiresInDays*24*60*60)  /* Expires after 2 days by default */
			]
		)
	} /* End if Ask Alexa notifications*/
	
	if (phoneNumber) {
		log.debug("sending text message")
		sendSms(phoneNumber, message)
	}
}

def getCustomImagePath() {
	return "https://raw.githubusercontent.com/yracine/device-type.myecobee/master/icons/"
}    


private def get_APP_NAME() {
	return "ecobeeRemoteSensorInit"
}