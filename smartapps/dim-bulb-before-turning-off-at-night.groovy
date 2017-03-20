m Bulb Before Turning Off At Night
 *
 *  Copyright 2017 Ian Young
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Dim Bulb Before Turning Off At Night",
    namespace: "duffrecords",
    author: "Ian Young",
    description: "Set the level of a bulb or switch to a specific value before turning it off.  Useful for setting bulbs to 1% brightness before the night before Gentle Wake Up is scheduled to run so that the bulbs do not start at 100%.  This will only happen between set hours.  Otherwise, the bulb will simply turn off.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Select a button to control the lights:") {
    	input "trigger", "capability.button", required: true, title: "Which?"
    }
	section("Dim lights to 1% and turn off when...") {
		input "offState", "enum", options: ["on", "off", "pushed", "held"], title: "button is", required: false
	}
    section("Turn on lights when...") {
    	input "onState", "enum", options: ["on", "off", "pushed", "held"], title: "button is", required: true
    }
	section("Which bulbs should be dimmed?") {
		input "bulbsToDim", "capability.switchLevel", required: true, multiple: true, title: "Which?"
	}
    section("Only dim between these times (optional)") {
        input "fromTime", "time", title: "From", required: false
        input "toTime", "time", title: "To", required: false
    }
    section("Set color temperature while dimming? (optional)") {
    	input "optionColorTemp", "number", title: "Which? (2700-6500)", required: false
    }
    section("If lights are already off, turn this light on instead (optional)") {
    	input "bulbToTurnOn", "capability.switchLevel", required: false, multiple: true, title: "Which?"
    }
    section("Set the above light to what value? (optional)") {
    	input "bulbOnLevel", "number", title: "Brightness (1-100)", required: false
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(trigger, "button.pushed", buttonHandler)
	subscribe(trigger, "button.on", buttonHandler)
	subscribe(trigger, "button.off", buttonHandler)
}

def buttonHandler(evt) {
	log.debug "in buttonHandler method"
    log.debug "button is ${evt.value}"
	if (evt.value == offState) {
    	log.debug "button turned off"
		checkSchedule()
	} else if (evt.value == onState) {
    	log.debug "button turned on"
		turnBulbsOn()
	}
}

def checkSchedule() {
    def currSwitches = bulbsToDim.currentSwitch
    def onSwitches = currSwitches.findAll { switchVal ->
        switchVal == "on" ? true : false
    }
    if (onSwitches.size() == 0 && bulbToTurnOn && bulbOnLevel) {
    	log.debug "all bulbs are off. turning selected bulb(s) on."
        bulbToTurnOn.setLevel(bulbOnLevel)
        bulbToTurnOn.on()
    } else {
	    if (fromTime && toTime) {
	    	def between = timeOfDayIsBetween(fromTime, toTime, new Date(), location.timeZone)
	        if (between) {
	        	log.debug "Event occurred within the scheduled times. Start dimming the bulbs."
	        	dimBulbs()
	        } else {
	        	log.debug "Event occurred outside the scheduled times. Just turn the bulbs off."
	            bulbsToDim.off()
	        }
	    } else {
	        log.debug "fromTime and toTime were not set, so dimming bulbs anyway."
	        dimBulbs()
	    }
    }
}

def dimBulbs() {
	bulbsToDim.each { bulb ->
	    if ( bulb.currentState("level").value.toInteger() > 1 ) {
    		bulb.setLevel(1)
        }
    }
    if (optionColorTemp) {
        log.debug "Setting color temperature to ${optionColorTemp}"
        bulbsToDim.setColorTemperature(optionColorTemp)
    }
    runIn(3, turnBulbsOff)
}

def turnBulbsOn() {
	log.debug "Turning bulbs on."
	bulbsToDim.on()
	bulbsToDim.setLevel(100)
}

def turnBulbsOff() {
    def bulbsState = bulbsToDim.currentState("level")
    //def bulbsRemaining = bulbsToDim.findAll { it.currentState("level").toInteger() > 1 }
    def bulbsRemaining = bulbsState.value.findAll { it.toInteger() > 1 }
    if (bulbsRemaining.size() == 0) {
		log.debug "all bulbs are at 1% brightness.  turning off."
        bulbsToDim.off()
    } else {
    	log.debug "bulb levels: ${bulbsState.value}.  waiting for all of them to reach 1%."
	    bulbsToDim.each { bulb ->
	    	if ( bulb.currentState("level").value.toInteger() > 1 ) {
	        	log.debug "${bulb} is not completely dimmed yet."
                bulb.setLevel(1)
	        }
	    }
        //bulbsRemaining.setLevel(1)
        runIn(3, turnBulbsOff)
    }
}
