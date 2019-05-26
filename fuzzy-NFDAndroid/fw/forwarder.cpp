/* -*- Mode:C++; c-file-style:"gnu"; indent-tabs-mode:nil; -*- */
/**
 * Copyright (c) 2014-2016,  Regents of the University of California,
 *                           Arizona Board of Regents,
 *                           Colorado State University,
 *                           University Pierre & Marie Curie, Sorbonne University,
 *                           Washington University in St. Louis,
 *                           Beijing Institute of Technology,
 *                           The University of Memphis.
 *
 * This file is part of NFD (Named Data Networking Forwarding Daemon).
 * See AUTHORS.md for complete list of NFD authors and contributors.
 *
 * NFD is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * NFD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * NFD, e.g., in COPYING.md file.  If not, see <http://www.gnu.org/licenses/>.
 */

#include "forwarder.hpp"
#include "pit-algorithm.hpp"
#include "core/logger.hpp"
#include "core/random.hpp"
#include "strategy.hpp"
#include "table/cleanup.hpp"
#include <ndn-cxx/lp/tags.hpp>
#include <boost/random/uniform_int_distribution.hpp>

#include <stdio.h>
#include <string.h>
#include <math.h>
#include <malloc.h>

#include <iostream>

namespace nfd {

NFD_LOG_INIT("Forwarder");

Forwarder::Forwarder()
  : m_unsolicitedDataPolicy(new fw::DefaultUnsolicitedDataPolicy())
  , m_fib(m_nameTree)
  , m_pit(m_nameTree)
  , m_measurements(m_nameTree)
  , m_strategyChoice(m_nameTree, fw::makeDefaultStrategy(*this))
{
  fw::installStrategies(*this);

  m_faceTable.afterAdd.connect([this] (Face& face) {
    face.afterReceiveInterest.connect(
      [this, &face] (const Interest& interest) {
        this->startProcessInterest(face, interest);
      });
    face.afterReceiveData.connect(
      [this, &face] (const Data& data) {
        this->startProcessData(face, data);
      });
    face.afterReceiveNack.connect(
      [this, &face] (const lp::Nack& nack) {
        this->startProcessNack(face, nack);
      });
  });

  m_faceTable.beforeRemove.connect([this] (Face& face) {
    cleanupOnFaceRemoval(m_nameTree, m_fib, m_pit, face);
  });
}

Forwarder::~Forwarder() = default;

void
Forwarder::startProcessInterest(Face& face, const Interest& interest)
{
  // check fields used by forwarding are well-formed
  try {
    if (interest.hasLink()) {
      interest.getLink();
    }
  }
  catch (const tlv::Error&) {
    NFD_LOG_DEBUG("startProcessInterest face=" << face.getId() <<
                  " interest=" << interest.getName() << " malformed");
    // It's safe to call interest.getName() because Name has been fully parsed
    return;
  }

  this->onIncomingInterest(face, interest);
}

void
Forwarder::startProcessData(Face& face, const Data& data)
{
  // check fields used by forwarding are well-formed
  // (none needed)

  this->onIncomingData(face, data);
}

void
Forwarder::startProcessNack(Face& face, const lp::Nack& nack)
{
  // check fields used by forwarding are well-formed
  try {
    if (nack.getInterest().hasLink()) {
      nack.getInterest().getLink();
    }
  }
  catch (const tlv::Error&) {
    NFD_LOG_DEBUG("startProcessNack face=" << face.getId() <<
                  " nack=" << nack.getInterest().getName() <<
                  "~" << nack.getReason() << " malformed");
    return;
  }

  this->onIncomingNack(face, nack);
}
std::string
Forwarder::getFuzzWord(std::string oldword)
{
     const long long max_size = 2000;         // max length of strings
     const long long N = 40;                  // number of closest words that will be shown
     const long long max_w = 50;              // max length of vocabulary entries


     FILE *f;
     char st1[max_size];

     strcpy(st1,oldword.c_str());

     char *bestw[N];
     char file_name[max_size], st[100][max_size];
     float dist, len, bestd[N], vec[max_size];
     long long words, size, a, b, c, d, cn, bi[100];
     char ch;
     float *M;
     char *vocab;

     std::string newword = "null";
     /*if (argc < 2) {
       printf("Usage: ./distance <FILE>\nwhere FILE contains word projections in the BINARY FORMAT\n");
       return 0;
     }*/

     const char* testfilePath = "/storage/emulated/0/yangliu.bin";
     strcpy(file_name, testfilePath);
     //jstring filepath = "/storage/emulated/0/360sichechk.txt";
     //const char* testfilePath = "/storage/emulated/0/vector.bin";
     NFD_LOG_WARN("yangl433-fopen(f)");
     f = fopen(file_name, "rb");
     if (f == NULL) {
         //printf("Input file not found\n");
         NFD_LOG_WARN("word2vector not found file!");

         return "null";
     }
     fscanf(f, "%lld", &words);
     fscanf(f, "%lld", &size);
     vocab = (char *)malloc((long long)words * max_w * sizeof(char));
     for (a = 0; a < N; a++) bestw[a] = (char *)malloc(max_size * sizeof(char));
     M = (float *)malloc((long long)words * (long long)size * sizeof(float));
     /*if (M == NULL) {
         printf("Cannot allocate memory: %lld MB    %lld  %lld\n", (long long)words * size * sizeof(float) / 1048576, words, size);
         return -1;
     }*/
     NFD_LOG_WARN("yangl433-for(b=0)");
     for (b = 0; b < words; b++) {
         a = 0;
         while (1) {
             vocab[b * max_w + a] = fgetc(f);
             if (feof(f) || (vocab[b * max_w + a] == ' ')) break;
             if ((a < max_w) && (vocab[b * max_w + a] != '\n')) a++;
         }
         vocab[b * max_w + a] = 0;
         for (a = 0; a < size; a++) fread(&M[a + b * size], sizeof(float), 1, f);
         len = 0;
         for (a = 0; a < size; a++) len += M[a + b * size] * M[a + b * size];
         len = sqrt(len);
         for (a = 0; a < size; a++) M[a + b * size] /= len;
     }
     fclose(f);
     NFD_LOG_WARN("yangl433-fclose(f)");
     bool isfirst = true;
     while (isfirst) {
         for (a = 0; a < N; a++) bestd[a] = 0;
         for (a = 0; a < N; a++) bestw[a][0] = 0;
         //printf("Enter word or sentence (EXIT to break): ");
         a = 0;
         /*while (1) {
           st1[a] = fgetc(stdin);
           if ((st1[a] == '\n') || (a >= max_size - 1)) {
             st1[a] = 0;
             break;
           }
           a++;
         }*/

         //if (!strcmp(st1, "EXIT")) break;
         cn = 0;
         b = 0;
         c = 0;
         while (1) {
             st[cn][b] = st1[c];
             b++;
             c++;
             st[cn][b] = 0;
             if (st1[c] == 0) break;
             if (st1[c] == ' ') {
                 cn++;
                 b = 0;
                 c++;
             }
         }
         cn++;
         for (a = 0; a < cn; a++) {
             for (b = 0; b < words; b++) if (!strcmp(&vocab[b * max_w], st[a])) break;
             if (b == words) b = -1;
             bi[a] = b;
             //printf("\nWord: %s  Position in vocabulary: %lld\n", st[a], bi[a]);
             if (b == -1) {
                 //printf("Out of dictionary word!\n");
                 break;
             }
         }
         if (b == -1) continue;
         //printf("\n                                              Word       Cosine distance\n------------------------------------------------------------------------\n");
         for (a = 0; a < size; a++) vec[a] = 0;
         for (b = 0; b < cn; b++) {
             if (bi[b] == -1) continue;
             for (a = 0; a < size; a++) vec[a] += M[a + bi[b] * size];
         }
         len = 0;
         for (a = 0; a < size; a++) len += vec[a] * vec[a];
         len = sqrt(len);
         for (a = 0; a < size; a++) vec[a] /= len;
         for (a = 0; a < N; a++) bestd[a] = -1;
         for (a = 0; a < N; a++) bestw[a][0] = 0;
         for (c = 0; c < words; c++) {
             a = 0;
             for (b = 0; b < cn; b++) if (bi[b] == c) a = 1;
             if (a == 1) continue;
             dist = 0;
             for (a = 0; a < size; a++) dist += vec[a] * M[a + c * size];
             for (a = 0; a < N; a++) {
                 if (dist > bestd[a]) {
                     for (d = N - 1; d > a; d--) {
                         bestd[d] = bestd[d - 1];
                         strcpy(bestw[d], bestw[d - 1]);
                     }
                     bestd[a] = dist;
                     strcpy(bestw[a], &vocab[c * max_w]);
                     break;
                 }
             }
         }
         NFD_LOG_WARN("yangl433-a = 0");
         //for (a = 0; a < N; a++) printf("%50s\t\t%f\n", bestw[a], bestd[a]);
         a = 1;
         newword = bestw[a];
         isfirst = false;

     }


     return newword;
}

/*const Name&
getNewName(const Name& name)
{
    bool isnew = false;
    //std::string name = interest.getName().toUri();
    std::string::size_type pos = name.find("/+");
    if(pos != std::string::npos)
        isnew = true;


    if(isnew)
        return std::string oldname = name.substr(0,pos);

    else
        return name;
}*/

void
Forwarder::onIncomingInterest(Face& inFace, const Interest& interest)
{
  // receive Interest
  NFD_LOG_DEBUG("onIncomingInterest face=" << inFace.getId() <<
                " interest=" << interest.getName());
  NFD_LOG_WARN("yangl433-onIncomingInterest face=" << inFace.getId() <<
                                " interest=" << interest.getName());
  NFD_LOG_WARN("yangl433-onIncomingInterest name.size()=" << interest.getqueriedName().size());
  interest.setTag(make_shared<lp::IncomingFaceIdTag>(inFace.getId()));
  ++m_counters.nInInterests;

  // /localhost scope control
  bool isViolatingLocalhost = inFace.getScope() == ndn::nfd::FACE_SCOPE_NON_LOCAL &&
                              scope_prefix::LOCALHOST.isPrefixOf(interest.getName());
  if (isViolatingLocalhost) {
    NFD_LOG_DEBUG("onIncomingInterest face=" << inFace.getId() <<
                  " interest=" << interest.getName() << " violates /localhost");
    // (drop)
    return;
  }

  // detect duplicate Nonce with Dead Nonce List
  bool hasDuplicateNonceInDnl = m_deadNonceList.has(interest.getName(), interest.getNonce());
  if (hasDuplicateNonceInDnl) {
    // goto Interest loop pipeline
    this->onInterestLoop(inFace, interest);
    return;
  }

  //detect InterestName is fuzzed
  std::string name = interest.getName().toUri();
  std::string::size_type pos = name.find("/queried");
  if(pos != std::string::npos){
    NFD_LOG_INFO("yangl433-It is a queried name :" << interest.getName());
    if(interest.getqueriedName().size() == 0)
        const_cast<Interest&>(interest).setqueriedName(interest.getName());
    //std::size_t found = name.find_last_of("/");
    //std::string oldname = name.substr(0,pos) + "/" + getFuzzWord(name.substr(found+1));
    std::string oldname = name.substr(0,pos);
    const_cast<Interest&>(interest).setName(oldname);
    NFD_LOG_INFO("yangl433-Interest`s name has changed :" << interest.getName());

  }



  /*if(isnew){
    std::string oldname = name.substr(0,pos);
    /*char st[2000];
    strcpy(st,name.c_str());
    int prefixLen = 0;
    for(int i = 0; i < pos ;i++)
    {
        if(st[i] == '/')
            prefixLen++;
    }*/
    //interest.setnewName(oname);
    //Name oldname = interest.getName();
    //
    //interest.setName(interest.getName().getPrefix(prefixLen));
    //const_cast<Interest&>(interest).setName(oldname);
    //NFD_LOG_INFO("yangl433-New name was changed to old Name :" << interest.getName());

  //}
  /*if (!interest.getForwardingHint().empty() &&
        m_networkRegionTable.isInProducerRegion(interest.getForwardingHint())) {
      NFD_LOG_DEBUG("onIncomingInterest face=" << inFace.getId() <<
                    " interest=" << interest.getName() << " reaching-producer-region");
      const_cast<Interest&>(interest).setForwardingHint({});
    }*/
  /*if(interest.hasLink()){

    NFD_LOG_INFO("yangl433-Interest hasLink :" << interest.hasLink());
    NFD_LOG_INFO("yangl433-Interest getDelegations :" << interest.getLink().getDelegations().begin()->second);

  }*/
    /*std::string name = interest.getName().toUri();
    std::string::size_type pos = name.find("/needFuzz");
    if(pos != std::string::npos){
      NFD_LOG_INFO("yangl433-It is a needFuzz name :" << interest.getName());
      std::size_t found = name.find_last_of("/");
      //std::string oldname = name.substr(0,pos) + "/" + getFuzzWord(name.substr(found+1));
      std::string oldname = name.substr(0,pos);
      const_cast<Interest&>(interest).setName(oldname);
      NFD_LOG_INFO("yangl433-Interest`s name has changed :" << interest.getName());

    }*/


  // PIT insert
  shared_ptr<pit::Entry> pitEntry = m_pit.insert(interest).first;


  // detect duplicate Nonce in PIT entry
  bool hasDuplicateNonceInPit = fw::findDuplicateNonce(*pitEntry, interest.getNonce(), inFace) !=
                                fw::DUPLICATE_NONCE_NONE;
  if (hasDuplicateNonceInPit) {
    // goto Interest loop pipeline
    this->onInterestLoop(inFace, interest);
    return;
  }

  // cancel unsatisfy & straggler timer
  this->cancelUnsatisfyAndStragglerTimer(*pitEntry);

  // is pending?
  if (!pitEntry->hasInRecords()) {



    m_cs.find(interest,
              bind(&Forwarder::onContentStoreHit, this, ref(inFace), pitEntry, _1, _2),
              bind(&Forwarder::onContentStoreMiss, this, ref(inFace), pitEntry, _1));
  }
  else {
    this->onContentStoreMiss(inFace, pitEntry, interest);
  }
}


void
Forwarder::onInterestLoop(Face& inFace, const Interest& interest)
{
  // if multi-access face, drop
  if (inFace.getLinkType() == ndn::nfd::LINK_TYPE_MULTI_ACCESS) {
    NFD_LOG_DEBUG("onInterestLoop face=" << inFace.getId() <<
                  " interest=" << interest.getName() <<
                  " drop");
    return;
  }

  NFD_LOG_DEBUG("onInterestLoop face=" << inFace.getId() <<
                " interest=" << interest.getName() <<
                " send-Nack-duplicate");

  // send Nack with reason=DUPLICATE
  // note: Don't enter outgoing Nack pipeline because it needs an in-record.
  lp::Nack nack(interest);
  nack.setReason(lp::NackReason::DUPLICATE);
  inFace.sendNack(nack);
}

void
Forwarder::onContentStoreMiss(const Face& inFace, const shared_ptr<pit::Entry>& pitEntry,
                              const Interest& interest)
{
  NFD_LOG_DEBUG("onContentStoreMiss interest=" << interest.getName());

  // insert in-record
  pitEntry->insertOrUpdateInRecord(const_cast<Face&>(inFace), interest);
  /*for (const pit::InRecord& inRecord : pitEntry->getInRecords()) {

              std::string inRInterestName = inRecord.getInterest().getName().toUri();
              NFD_LOG_INFO("yangl433-pitEntry inRecords interest name" << inRInterestName);

   }*/
  // set PIT unsatisfy timer
  this->setUnsatisfyTimer(pitEntry);

  // has NextHopFaceId?
  shared_ptr<lp::NextHopFaceIdTag> nextHopTag = interest.getTag<lp::NextHopFaceIdTag>();
  if (nextHopTag != nullptr) {
    // chosen NextHop face exists?
    Face* nextHopFace = m_faceTable.get(*nextHopTag);
    if (nextHopFace != nullptr) {
      // go to outgoing Interest pipeline
      this->onOutgoingInterest(pitEntry, *nextHopFace);
    }
    return;
  }
  //getfuzz name
    /*std::string name = interest.getName().toUri();
    std::string::size_type pos = name.find("/query");
    if(pos != std::string::npos){
      NFD_LOG_INFO("yangl433-It is a query name :" << interest.getName());
      if(interest.getqueriedName().size() != 0){
          NFD_LOG_INFO("yangl433-query Interest has queried name :" << interest.getqueriedName());
          const_cast<Interest&>(interest).setName(interest.getqueriedName());
          NFD_LOG_INFO("yangl433-query Interest has changed name :" << interest.getName());
      }
      else{
          NFD_LOG_INFO("yangl433-query Interest doesn`t have queried name");
          std::size_t found = name.find_last_of("/");
          std::string oldname = name + "/queried/" + getFuzzWord(name.substr(found+1));

          const_cast<Interest&>(interest).setName(oldname);
          NFD_LOG_INFO("yangl433-query* Interest`s name has changed :" << interest.getName());
      }

    }
    NFD_LOG_INFO("yangl433-pitEntry name" << pitEntry->getName());

    //NFD_LOG_INFO("yangl433-pitEntry inRecords interest name" << inRecord.getInterest().getName());
    for (const pit::InRecord& inRecord : pitEntry->getInRecords()) {

              std::string inRInterestName = inRecord.getInterest().getName().toUri();
              NFD_LOG_INFO("yangl433-pitEntry inRecords interest name" << inRInterestName);

        }*/
    //NFD_LOG_INFO("yangl433-pitEntry interest name :" << pitEntry.getInterest().getName());
  // dispatch to strategy: after incoming Interest
  this->dispatchToStrategy(*pitEntry,
    [&] (fw::Strategy& strategy) { strategy.afterReceiveInterest(inFace, interest, pitEntry); });
}

void
Forwarder::onContentStoreHit(const Face& inFace, const shared_ptr<pit::Entry>& pitEntry,
                             const Interest& interest, const Data& data)
{
  NFD_LOG_DEBUG("onContentStoreHit interest=" << interest.getName());

  data.setTag(make_shared<lp::IncomingFaceIdTag>(face::FACEID_CONTENT_STORE));
  // XXX should we lookup PIT for other Interests that also match csMatch?

  // set PIT straggler timer
  this->setStragglerTimer(pitEntry, true, data.getFreshnessPeriod());

  // goto outgoing Data pipeline
  this->onOutgoingData(data, *const_pointer_cast<Face>(inFace.shared_from_this()));
}

void
Forwarder::onOutgoingInterest(const shared_ptr<pit::Entry>& pitEntry, Face& outFace,
                              bool wantNewNonce)
{
  if (outFace.getId() == face::INVALID_FACEID) {
    NFD_LOG_WARN("onOutgoingInterest face=invalid interest=" << pitEntry->getName());
    return;
  }
  NFD_LOG_DEBUG("onOutgoingInterest face=" << outFace.getId() <<
                " interest=" << pitEntry->getName());
  NFD_LOG_INFO("yangl433-onOutgoingInterest face=" << outFace.getId() <<
                                " interest=" << pitEntry->getName());

  // scope control
  if (fw::violatesScope(*pitEntry, outFace)) {
    NFD_LOG_DEBUG("onOutgoingInterest face=" << outFace.getId() <<
                  " interest=" << pitEntry->getName() << " violates scope");
    return;
  }

  // pick Interest
  // The outgoing Interest picked is the last incoming Interest that does not come from outFace.
  // If all in-records come from outFace, it's fine to pick that.
  // This happens when there's only one in-record that comes from outFace.
  // The legit use is for vehicular network; otherwise, strategy shouldn't send to the sole inFace.
  pit::InRecordCollection::iterator pickedInRecord = std::max_element(
    pitEntry->in_begin(), pitEntry->in_end(),
    [&outFace] (const pit::InRecord& a, const pit::InRecord& b) {
      bool isOutFaceA = &a.getFace() == &outFace;
      bool isOutFaceB = &b.getFace() == &outFace;
      return (isOutFaceA > isOutFaceB) ||
             (isOutFaceA == isOutFaceB && a.getLastRenewed() < b.getLastRenewed());
    });
  BOOST_ASSERT(pickedInRecord != pitEntry->in_end());
  auto interest = const_pointer_cast<Interest>(pickedInRecord->getInterest().shared_from_this());

  if (wantNewNonce) {
    interest = make_shared<Interest>(*interest);
    static boost::random::uniform_int_distribution<uint32_t> dist;
    interest->setNonce(dist(getGlobalRng()));
  }

  // insert out-record
  pitEntry->insertOrUpdateOutRecord(outFace, *interest);

//getfuzz name
    std::string name = pickedInRecord->getInterest().getName().toUri();
    std::string::size_type pos = name.find("/query");
    if(pos != std::string::npos){
      NFD_LOG_INFO("yangl433-It is a query name :" << pickedInRecord->getInterest().getName());
      NFD_LOG_INFO("yangl433-pitEntry name" << pitEntry->getName());
      if(pickedInRecord->getInterest().getqueriedName().size() != 0){
          NFD_LOG_INFO("yangl433-query Interest has queried name :" << pickedInRecord->getInterest().getqueriedName());
          const_cast<Interest&>(pickedInRecord->getInterest()).setName(pickedInRecord->getInterest().getqueriedName());
          NFD_LOG_INFO("yangl433-query Interest has changed name :" << pickedInRecord->getInterest().getName());
      }
      else{
          NFD_LOG_INFO("yangl433-query Interest doesn`t have queried name");
          std::size_t found = name.find_last_of("/");
          std::string oldname = name + "/queried/" + getFuzzWord(name.substr(found+1));

          const_cast<Interest&>(pickedInRecord->getInterest()).setName(oldname);
          NFD_LOG_INFO("yangl433-query* Interest`s name has changed :" << pickedInRecord->getInterest().getName());
      }
      //NFD_LOG_INFO("yangl433-pitEntry name" << pitEntry->getName());

          //NFD_LOG_INFO("yangl433-pitEntry inRecords interest name" << inRecord.getInterest().getName());
      for (const pit::InRecord& inRecord : pitEntry->getInRecords()) {

                std::string inRInterestName = inRecord.getInterest().getName().toUri();
                NFD_LOG_INFO("yangl433-pitEntry inRecords interest name" << inRInterestName);

          }
      outFace.sendInterest(*interest);
        ++m_counters.nOutInterests;
        //restore pitinRecord
        const_cast<Interest&>(pickedInRecord->getInterest()).setName(name);
        for (const pit::InRecord& inRecord : pitEntry->getInRecords()) {

                        std::string inRInterestName = inRecord.getInterest().getName().toUri();
                        NFD_LOG_INFO("yangl433-pitEntry inRecords backed interest name" << inRInterestName);

                  }

    }
    //NFD_LOG_INFO("yangl433-pitEntry name" << pitEntry->getName());

    //NFD_LOG_INFO("yangl433-pitEntry inRecords interest name" << inRecord.getInterest().getName());
    else{
        for (const pit::InRecord& inRecord : pitEntry->getInRecords()) {


                  std::string inRInterestName = inRecord.getInterest().getName().toUri();
                  NFD_LOG_INFO("yangl433-pitEntry inRecords interest name" << inRInterestName);

            }
        // send Interest
        //NFD_LOG_INFO("yangl433-*interest name" << *interest->getName());
        outFace.sendInterest(*interest);
        ++m_counters.nOutInterests;
    }
}

void
Forwarder::onInterestReject(const shared_ptr<pit::Entry>& pitEntry)
{
  if (fw::hasPendingOutRecords(*pitEntry)) {
    NFD_LOG_ERROR("onInterestReject interest=" << pitEntry->getName() <<
                  " cannot reject forwarded Interest");
    return;
  }
  NFD_LOG_DEBUG("onInterestReject interest=" << pitEntry->getName());

  // cancel unsatisfy & straggler timer
  this->cancelUnsatisfyAndStragglerTimer(*pitEntry);

  // set PIT straggler timer
  this->setStragglerTimer(pitEntry, false);
}

void
Forwarder::onInterestUnsatisfied(const shared_ptr<pit::Entry>& pitEntry)
{
  NFD_LOG_DEBUG("onInterestUnsatisfied interest=" << pitEntry->getName());

  // invoke PIT unsatisfied callback
  this->dispatchToStrategy(*pitEntry,
    [&] (fw::Strategy& strategy) { strategy.beforeExpirePendingInterest(pitEntry); });

  // goto Interest Finalize pipeline
  this->onInterestFinalize(pitEntry, false);
}

void
Forwarder::onInterestFinalize(const shared_ptr<pit::Entry>& pitEntry, bool isSatisfied,
                              time::milliseconds dataFreshnessPeriod)
{
  NFD_LOG_DEBUG("onInterestFinalize interest=" << pitEntry->getName() <<
                (isSatisfied ? " satisfied" : " unsatisfied"));

  // Dead Nonce List insert if necessary
  this->insertDeadNonceList(*pitEntry, isSatisfied, dataFreshnessPeriod, 0);

  // PIT delete
  this->cancelUnsatisfyAndStragglerTimer(*pitEntry);
  m_pit.erase(pitEntry.get());
}
std::string
Forwarder::getpartName(std::string name)
{
    int num = 0;
    int locate = 0;
    std::string partName;
    while( locate < name.size())
    {

      if(partName[locate] == '/')
          num += 1;
      if(num == 3){
            break;
      }
      locate++;
    }

    partName = name.substr(locate);

    return partName;
}

void
Forwarder::onIncomingData(Face& inFace, const Data& data)
{
  // receive Data
  NFD_LOG_DEBUG("onIncomingData face=" << inFace.getId() << " data=" << data.getName());
  NFD_LOG_WARN("yangl433-onincomingData" << inFace.getId() << " data=" << data.getName());
  data.setTag(make_shared<lp::IncomingFaceIdTag>(inFace.getId()));
  ++m_counters.nInData;



  // /localhost scope control
  bool isViolatingLocalhost = inFace.getScope() == ndn::nfd::FACE_SCOPE_NON_LOCAL &&
                              scope_prefix::LOCALHOST.isPrefixOf(data.getName());
  if (isViolatingLocalhost) {
    NFD_LOG_DEBUG("onIncomingData face=" << inFace.getId() <<
                  " data=" << data.getName() << " violates /localhost");
    // (drop)
    return;
  }
  // fuzzed data restore


    std::string name = data.getName().toUri();
    std::string::size_type pos = name.find("/queried");
    if(pos != std::string::npos){
      NFD_LOG_INFO("yangl433-dataname is a queried name :" << data.getName());
      //std::size_t found = name.find_last_of("/");
      ///std::string name1 = name.substr(found+1);
      //std::string partname = getpartName(name.substr(pos,found+1));


      std::string oldname = name.substr(0,pos); //+ partname;
      const_cast<Data&>(data).setName(oldname);
      NFD_LOG_INFO("yangl433-Data`s name has changed :" << data.getName());

    }

  // PIT match
  pit::DataMatchResult pitMatches = m_pit.findAllDataMatches(data);
  if (pitMatches.begin() == pitMatches.end()) {
    // goto Data unsolicited pipeline
    NFD_LOG_INFO("yangl433-Data doesn `t have pitmatch ");
    this->onDataUnsolicited(inFace, data);
    return;
  }

  // CS insert
  m_cs.insert(data);

  std::set<Face*> pendingDownstreams;
  // foreach PitEntry
  auto now = time::steady_clock::now();
  for (const shared_ptr<pit::Entry>& pitEntry : pitMatches) {
    NFD_LOG_DEBUG("onIncomingData matching=" << pitEntry->getName());
    NFD_LOG_WARN("yangl433-onIncomingData matching=" << pitEntry->getName());



    // cancel unsatisfy & straggler timer
    this->cancelUnsatisfyAndStragglerTimer(*pitEntry);

    // remember pending downstreams //yangl
    for (const pit::InRecord& inRecord : pitEntry->getInRecords()) {
      if (inRecord.getExpiry() > now) {
          /*std::string inRInterestName = inRecord.getInterest().getName().toUri();
          std::string::size_type pos = inRInterestName.find("/needFuzz");
          if(pos != std::string::npos){
              inRecord.getFace().setIsNeed(true);
          }*/
        pendingDownstreams.insert(&inRecord.getFace());
      }
    }

    // invoke PIT satisfy callback
    this->dispatchToStrategy(*pitEntry,
      [&] (fw::Strategy& strategy) { strategy.beforeSatisfyInterest(pitEntry, inFace, data); });

    // Dead Nonce List insert if necessary (for out-record of inFace)
    this->insertDeadNonceList(*pitEntry, true, data.getFreshnessPeriod(), &inFace);

    // mark PIT satisfied
    pitEntry->clearInRecords();
    pitEntry->deleteOutRecord(inFace);

    // set PIT straggler timer
    this->setStragglerTimer(pitEntry, true, data.getFreshnessPeriod());
  }
  //change data name



  // foreach pending downstream
  for (Face* pendingDownstream : pendingDownstreams) {
    if (pendingDownstream == &inFace) {
      continue;
    }
    // goto outgoing Data pipeline
    this->onOutgoingData(data, *pendingDownstream);
  }
}

void
Forwarder::onDataUnsolicited(Face& inFace, const Data& data)
{
  // accept to cache?
  fw::UnsolicitedDataDecision decision = m_unsolicitedDataPolicy->decide(inFace, data);
  if (decision == fw::UnsolicitedDataDecision::CACHE) {
    // CS insert
    m_cs.insert(data, true);
  }

  NFD_LOG_DEBUG("onDataUnsolicited face=" << inFace.getId() <<
                " data=" << data.getName() <<
                " decision=" << decision);
}

void
Forwarder::onOutgoingData(const Data& data, Face& outFace)
{
  if (outFace.getId() == face::INVALID_FACEID) {
    NFD_LOG_WARN("onOutgoingData face=invalid data=" << data.getName());
    return;
  }
  NFD_LOG_DEBUG("onOutgoingData face=" << outFace.getId() << " data=" << data.getName());

  // /localhost scope control
  bool isViolatingLocalhost = outFace.getScope() == ndn::nfd::FACE_SCOPE_NON_LOCAL &&
                              scope_prefix::LOCALHOST.isPrefixOf(data.getName());
  if (isViolatingLocalhost) {
    NFD_LOG_DEBUG("onOutgoingData face=" << outFace.getId() <<
                  " data=" << data.getName() << " violates /localhost");
    // (drop)
    return;
  }
  //yangl433
  /*if(outFace.isNeed){
      NFD_LOG_INFO("yangl433-outFace is a needFuzz face :" << outFace.isNeed);
      std::string name = data.getName().toUri();
      std::string::size_type pos = name.find("/general");
      if(pos != std::string::npos){
        NFD_LOG_INFO("yangl433-dataname is a general name :" << data.getName());
        std::size_t found = name.find_last_of("/");
        std::string oldname = name.substr(0,pos+8) + "/needFuzz/teacher" + name.substr(pos+8,found+1);
        const_cast<Data&>(data).setName(oldname);
        NFD_LOG_INFO("yangl433-Data`s name has changed :" << data.getName());

      }
      outFace.setIsNeed(false);
      NFD_LOG_INFO("yangl433-needFuzz face has changed to not needFuzz face :" << outFace.isNeed);
      outFace.sendData(data);
      ++m_counters.nOutData;
      const_cast<Data&>(data).setName(name);
      return;

  }*/

  // TODO traffic manager

  // send Data
  outFace.sendData(data);
  ++m_counters.nOutData;
}

void
Forwarder::onIncomingNack(Face& inFace, const lp::Nack& nack)
{
  // receive Nack
  nack.setTag(make_shared<lp::IncomingFaceIdTag>(inFace.getId()));
  ++m_counters.nInNacks;

  // if multi-access face, drop
  if (inFace.getLinkType() == ndn::nfd::LINK_TYPE_MULTI_ACCESS) {
    NFD_LOG_DEBUG("onIncomingNack face=" << inFace.getId() <<
                  " nack=" << nack.getInterest().getName() <<
                  "~" << nack.getReason() << " face-is-multi-access");
    return;
  }

  // PIT match
  shared_ptr<pit::Entry> pitEntry = m_pit.find(nack.getInterest());
  // if no PIT entry found, drop
  if (pitEntry == nullptr) {
    NFD_LOG_DEBUG("onIncomingNack face=" << inFace.getId() <<
                  " nack=" << nack.getInterest().getName() <<
                  "~" << nack.getReason() << " no-PIT-entry");
    return;
  }

  // has out-record?
  pit::OutRecordCollection::iterator outRecord = pitEntry->getOutRecord(inFace);
  // if no out-record found, drop
  if (outRecord == pitEntry->out_end()) {
    NFD_LOG_DEBUG("onIncomingNack face=" << inFace.getId() <<
                  " nack=" << nack.getInterest().getName() <<
                  "~" << nack.getReason() << " no-out-record");
    return;
  }

  // if out-record has different Nonce, drop
  if (nack.getInterest().getNonce() != outRecord->getLastNonce()) {
    NFD_LOG_DEBUG("onIncomingNack face=" << inFace.getId() <<
                  " nack=" << nack.getInterest().getName() <<
                  "~" << nack.getReason() << " wrong-Nonce " <<
                  nack.getInterest().getNonce() << "!=" << outRecord->getLastNonce());
    return;
  }

  NFD_LOG_DEBUG("onIncomingNack face=" << inFace.getId() <<
                " nack=" << nack.getInterest().getName() <<
                "~" << nack.getReason() << " OK");

  // record Nack on out-record
  outRecord->setIncomingNack(nack);

  // trigger strategy: after receive NACK
  this->dispatchToStrategy(*pitEntry,
    [&] (fw::Strategy& strategy) { strategy.afterReceiveNack(inFace, nack, pitEntry); });
}

void
Forwarder::onOutgoingNack(const shared_ptr<pit::Entry>& pitEntry, const Face& outFace,
                          const lp::NackHeader& nack)
{
  if (outFace.getId() == face::INVALID_FACEID) {
    NFD_LOG_WARN("onOutgoingNack face=invalid" <<
                  " nack=" << pitEntry->getInterest().getName() <<
                  "~" << nack.getReason() << " no-in-record");
    return;
  }

  // has in-record?
  pit::InRecordCollection::iterator inRecord = pitEntry->getInRecord(outFace);

  // if no in-record found, drop
  if (inRecord == pitEntry->in_end()) {
    NFD_LOG_DEBUG("onOutgoingNack face=" << outFace.getId() <<
                  " nack=" << pitEntry->getInterest().getName() <<
                  "~" << nack.getReason() << " no-in-record");
    return;
  }

  // if multi-access face, drop
  if (outFace.getLinkType() == ndn::nfd::LINK_TYPE_MULTI_ACCESS) {
    NFD_LOG_DEBUG("onOutgoingNack face=" << outFace.getId() <<
                  " nack=" << pitEntry->getInterest().getName() <<
                  "~" << nack.getReason() << " face-is-multi-access");
    return;
  }

  NFD_LOG_DEBUG("onOutgoingNack face=" << outFace.getId() <<
                " nack=" << pitEntry->getInterest().getName() <<
                "~" << nack.getReason() << " OK");

  // create Nack packet with the Interest from in-record
  lp::Nack nackPkt(inRecord->getInterest());
  nackPkt.setHeader(nack);

  // erase in-record
  pitEntry->deleteInRecord(outFace);

  // send Nack on face
  const_cast<Face&>(outFace).sendNack(nackPkt);
  ++m_counters.nOutNacks;
}

static inline bool
compare_InRecord_expiry(const pit::InRecord& a, const pit::InRecord& b)
{
  return a.getExpiry() < b.getExpiry();
}

void
Forwarder::setUnsatisfyTimer(const shared_ptr<pit::Entry>& pitEntry)
{
  pit::InRecordCollection::iterator lastExpiring =
    std::max_element(pitEntry->in_begin(), pitEntry->in_end(), &compare_InRecord_expiry);

  time::steady_clock::TimePoint lastExpiry = lastExpiring->getExpiry();
  time::nanoseconds lastExpiryFromNow = lastExpiry - time::steady_clock::now();
  if (lastExpiryFromNow <= time::seconds::zero()) {
    // TODO all in-records are already expired; will this happen?
  }

  scheduler::cancel(pitEntry->m_unsatisfyTimer);
  pitEntry->m_unsatisfyTimer = scheduler::schedule(lastExpiryFromNow,
    bind(&Forwarder::onInterestUnsatisfied, this, pitEntry));
}

void
Forwarder::setStragglerTimer(const shared_ptr<pit::Entry>& pitEntry, bool isSatisfied,
                             time::milliseconds dataFreshnessPeriod)
{
  time::nanoseconds stragglerTime = time::milliseconds(100);

  scheduler::cancel(pitEntry->m_stragglerTimer);
  pitEntry->m_stragglerTimer = scheduler::schedule(stragglerTime,
    bind(&Forwarder::onInterestFinalize, this, pitEntry, isSatisfied, dataFreshnessPeriod));
}

void
Forwarder::cancelUnsatisfyAndStragglerTimer(pit::Entry& pitEntry)
{
  scheduler::cancel(pitEntry.m_unsatisfyTimer);
  scheduler::cancel(pitEntry.m_stragglerTimer);
}

static inline void
insertNonceToDnl(DeadNonceList& dnl, const pit::Entry& pitEntry,
                 const pit::OutRecord& outRecord)
{
  dnl.add(pitEntry.getName(), outRecord.getLastNonce());
}

void
Forwarder::insertDeadNonceList(pit::Entry& pitEntry, bool isSatisfied,
                               time::milliseconds dataFreshnessPeriod, Face* upstream)
{
  // need Dead Nonce List insert?
  bool needDnl = false;
  if (isSatisfied) {
    bool hasFreshnessPeriod = dataFreshnessPeriod >= time::milliseconds::zero();
    // Data never becomes stale if it doesn't have FreshnessPeriod field
    needDnl = static_cast<bool>(pitEntry.getInterest().getMustBeFresh()) &&
              (hasFreshnessPeriod && dataFreshnessPeriod < m_deadNonceList.getLifetime());
  }
  else {
    needDnl = true;
  }

  if (!needDnl) {
    return;
  }

  // Dead Nonce List insert
  if (upstream == 0) {
    // insert all outgoing Nonces
    const pit::OutRecordCollection& outRecords = pitEntry.getOutRecords();
    std::for_each(outRecords.begin(), outRecords.end(),
                  bind(&insertNonceToDnl, ref(m_deadNonceList), cref(pitEntry), _1));
  }
  else {
    // insert outgoing Nonce of a specific face
    pit::OutRecordCollection::iterator outRecord = pitEntry.getOutRecord(*upstream);
    if (outRecord != pitEntry.getOutRecords().end()) {
      m_deadNonceList.add(pitEntry.getName(), outRecord->getLastNonce());
    }
  }
}


} // namespace nfd
