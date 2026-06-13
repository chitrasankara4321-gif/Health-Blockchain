// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract HealthRecord {

    struct Record {
        string fileHash;
        address patient;
        address doctor;
        uint timestamp;
    }

    mapping(uint => Record) public records;
    uint public recordCount;

    event RecordAdded(uint id, string hash, address patient);
    event AccessGranted(uint id, address doctor);

    function addRecord(string memory _hash) public {
        recordCount++;
        records[recordCount] = Record(_hash, msg.sender, address(0), block.timestamp);
        emit RecordAdded(recordCount, _hash, msg.sender);
    }

    function grantAccess(uint _id, address _doctor) public {
        require(records[_id].patient == msg.sender, "Not owner");
        records[_id].doctor = _doctor;
        emit AccessGranted(_id, _doctor);
    }
}


