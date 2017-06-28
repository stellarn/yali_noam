/*
 * MarineElement.h
 *
 *  Created on: 27 Jun 2017
 *      Author: noam
 */

#ifndef MARINEELEMENT_H_
#define MARINEELEMENT_H_

#include <utility>
#include <string>

using std::pair;
using std::string;

using coordinates = pair<int, int>;

class Marine_Element {

private:
    string name;
    coordinates position; // position in the seven seas.
    double fuel_tank;

    
public:
	Marine_Element();
	virtual ~Marine_Element()=0;
    
    coordinates getPosition() const {return position;};
    void setPosition(int x, int y){ position.first=x; position.second=y;};

    
    double getFuel()const{return fuel_tank;};
    void setFuel(double fuel){fuel_tank=fuel;};
    
    virtual void go()=0;
    
};

#endif /* MARINEELEMENT_H_ */
