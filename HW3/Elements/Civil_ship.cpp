//
//  Civil_ship.cpp
//  hw3
//
//  Created by Noam Stolero on 01/07/2017.
//  Copyright © 2017 Noam Stolero. All rights reserved.
//

#include "Civil_ship.h"
#include "Port.h"
#include "../Utilities/CivilShipsCommands.h"

Civil_ship::Civil_ship(Type t, string name, Point pos, double fuel,double lpm):Ship(t,name,pos,fuel, lpm),fuelling(false)
{
}

//Dock at destination port, if not in range move to it.
bool Civil_ship::dock(weak_ptr<Port> port)
{
    //if not current destination, start to move toward destination.
    if (destination.lock()!=port.lock())
    {
        setDestination(port);
    }
    
    // try to dock.
    Point portPos=port.lock()->getPosition();
    if (Ship::inRange(portPos,DOCKING_RANGE))
    {
        Ship::set_state(State::DOCKED);
        return true;
    }
    return false; //failed to dock.
}

// fuel ship.
void Civil_ship::fuel(double fuel)
{
    Marine_Element::addFuel(fuel);
}

//for fueling purposes.
double Civil_ship::getRequiredFuelAmount()const
{
    return Marine_Element::getFuelCapcity()-Marine_Element::getCurrentFuel();
}


//set destiantion to given port.
void Civil_ship::setDestination(weak_ptr<Port> port)
{
    destination=port;
    Ship::calculate_route();
    Ship::set_state(State::MOVING);
}

//enqueue ciivil ship commands.
void Civil_ship::enqueue(Civil_Ships_Commands *csc)
{
    Ship::enqueue(csc);
}


//add ship to fuel queue of port.
bool Civil_ship::refuel()
{
    if (Ship::get_state()==State::DOCKED)
    {
        destination.lock()->fuel_request(this);
        return true;    //entering fuel queue.
    }
    return false;   //Must be at Dock state.
}

//set fuelling flag.
void Civil_ship::set_Waiting_for_fuel(bool b)
{
    fuelling=b;
}

void Civil_ship::pritorityCommand(Civil_Ships_Commands *csc)
{
    Ship::pritorityCommand(csc);
}
