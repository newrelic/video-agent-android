//
// Created by Andreu Santaren on 13/11/2018.
//

#include "StupidTest.hpp"
#include <stdio.h>

void StupidTest::sayHello(std::string name) {
    printf("%s\n", returnHello(name).c_str());
}

std::string StupidTest::returnHello(std::string name) {
    return "Hello " + name;
}
