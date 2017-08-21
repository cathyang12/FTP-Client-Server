#ifndef _TYSRTRUC__

#define _TYSRTRUC__
//this file contains structs and methods
//that are often used by multiple FTP commands

//create the boolean type, C does not have build-in boolean

typedef enum { false,
               true } bool;

typedef enum {
    DIR = 0,
    CWD = 1,
    CDUP = 2,
    TYPE = 3,
    MODE = 4,
    STRU = 5,
    RETR = 6,
    PASV = 7,
    NLST = 8,
    USER = 9, //USER and QUIT are the higher ones so that
    QUIT = 10 //checking if user is allowed a command is easier (>8)
} ftpCommands;
//#define BACKLOG 1

typedef struct
{
    ftpCommands command;
    char *argument;
} userCommand;

typedef struct
{
    userCommand *comm; //last user command (if any)
    bool isLogin;      //is this user logged in
    char *noPar;       //directory of the ftp server (this is set once and stays constant)
    char *curWorDir;   //current working directory
    int new_fd;        //current file descriptor socket used for current connection
    int pas_fd;        //passive file descriptor
    bool isPasv;       //is the user in pasv mode
} programState;
#endif
