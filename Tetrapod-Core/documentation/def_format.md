Tetrapod .def format
====================

This is a line oriented format.  Upon each line there can be
directives, tags, and comments.

      # example
      java package io.tetrapod.protocol.sample
      java outdir src
      
      service Sample @version(1) @id(999)
      default security protected 
      
      const int GLOBAL_CONST = 42
      
      // A simple password based login
      public request Login 
         1: string username  // username
         2: string password  // password .. should never be sent over plaintext socket

         error USER_UNKNOWN, WRONG_PASSWORD
         
      public response Login
         1: int accountId
         2: string authToken
         
      internal message Users.Update
         1: int accountId
         2: int entityId
         3: byte status
         
         const byte LOGGED_IN = 1 
         const byte LOGGED_OUT = 2 

comments
--------

Comments are until the end of line, starting with two slashes //. or starting with #.

* Comments starting with *#* are purely discarded.  They are for comments inside the
  .def file.

* Comments starting with *//* are preserved and attached to the directive they share a 
line with, or if they are on a line by themselves then the immediately 
following directive.  Multiple sequential comments are combined.  What 
happens then is up to the language generator although in general the 
idea is that becomes the in-code comment for the generated element.
 
tags
----

Tags are used for a number of miscellanious directive-level features.
Their general format is 

* @tagname("comma", "seperated", "list")
* the comma-separated list is optional
* the elements of the list do not need to be quoted if they are just 
  numbers and letters
  
The currently used tags are:
* @id(N) : manually sets the id to N (otherwise the name is hashed).  Required for services, optional on classes
* @version(N)  : sets the version to N.  Required for services
* @web(NAME)
  * on a service, turns on web routing with NAME as a prefix to all routes
  * on a request, turns on web routing with service refix + NAME being the name of the route
  * on a field, sets the field's name to NAME for json web serialization (default is the field name)
* @noweb : on a field, marks it as being left out of the json serialization, means it will be at default

directives
----------

There are quite a few directives although they have just a few groups.

  * the class directives define request, response, messages, and structs
  
  * the field messages define instance fields and consts
  
  * miscellanious one-off directives to pass options to the language processors
    and do overall configuration 

   
class directives
----------------

SECURITY {request|response|struct} NAME
  * eg: public request Login
  * generates a request, response, or struct of name NAME
  * responses typically have the same name as the request
  * tags: can have @id, @web (see tags)
  
SECURITY message SUB.NAME
  * generates a message of name NAME
  * also generate a subscription of name SUB and makes this message a part of it
  * the SUB. is optional
  * tags: can have @id, @web (see tags)
  
SECURITY
  * _public_ means available to an unauthenticated user (and admin, and all services)
  * _protected_ means available to an authenticated user (and admin, and all services)
  * _internal_ means available to all services (and admin)
  * _private_ means available to just the specific services (and not admin)
  * _admin_ means available just to the admin (and not anyone else)
  * security is optional
  
field directives
----------------

TAG: TYPE NAME = DEFAULT
  * defines a field of type TYPE, with name NAME, at tag TAG.  it gets initialized 
    to the given default value
  * specifying a default value is options
  * must be defined after a class directive, belongs to that class
  * tags: accepts @web, @noweb, @id (see tags section)

TYPES
  * primitives: boolean, byte, int, long, double, string
  * structs: any struct
  * collections: append [] to the type to make it an array, or <list> to make it a
    list.  The wire format is the same for both, it's up to the language if there is
    a difference (in Java a list a List type)
   
const TYPE NAME = DEFAULT
  * Specifies a constant.  Not specifying a default will not cause an error in the code
    generator but will in the compiled code fo most languages

error NAME = DEFAULT
  * specifies an error with a specific error code
  
error NAME1, NAME2, NAME3, ...
  * specifies a number of errors, each with no specified code (the name will be hashed)
  
  
misc directives
----------------

java package PACKAGENAME
   * specifies that if compiled for Java the service is to be placed in PACKAGENAME package

java outdir SOURCEDIR
   * specifies that if compiled for Java the root is the source tree is SOURCEDIR

service NAME @version(V)
  * service with name of NAME and version of V
  * tags: can have @id, @web (see tags)
  
global scope
  * reintroduces global scope for putting global constants and errors at the end
    of the file 
     
default security SECURITY
  * set the default security level to SECURITY (see class directive)