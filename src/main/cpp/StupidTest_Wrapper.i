// Module Name
%module StupidTest_Wrapper

// Included stuff
%{
#include "StupidTest.hpp"
%}

// The prototypes to convert
%include "std_string.i"
%include "StupidTest.hpp"
